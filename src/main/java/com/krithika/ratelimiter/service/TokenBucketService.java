package com.krithika.ratelimiter.service;

import com.krithika.ratelimiter.dto.RateLimitResult;
import com.krithika.ratelimiter.model.ApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * TOKEN BUCKET ALGORITHM
 * ──────────────────────
 * Concept:
 *   - A bucket holds tokens up to `capacity`.
 *   - Every request consumes 1 token.
 *   - Tokens refill at a fixed rate (refillTokens every refillIntervalSeconds).
 *   - If the bucket is empty → request is rejected (429).
 *
 * Atomicity:
 *   The refill-and-consume is executed as a single Lua script (token_bucket.lua).
 *   Redis runs scripts atomically on its single command thread, so concurrent
 *   requests for the same key cannot interleave the read-modify-write. This is
 *   the key difference from a naive version that issues separate GET/SET calls:
 *   that version has a race window where two callers both read the same token
 *   count and both decrement it, over-admitting requests. The script collapses
 *   the whole decision into one round trip — correct under concurrency AND faster.
 *
 * Redis keys used:
 *   rate:tb:{apiKey}:tokens   → current token count   (String)
 *   rate:tb:{apiKey}:last     → last refill timestamp (epoch seconds, String)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBucketService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rate-limiter.token-bucket.capacity:100}")
    private int defaultCapacity;

    @Value("${rate-limiter.token-bucket.refill-tokens:10}")
    private int defaultRefillTokens;

    @Value("${rate-limiter.token-bucket.refill-interval-seconds:1}")
    private int defaultRefillIntervalSeconds;

    @Value("${rate-limiter.alert.redis-key-ttl-seconds:3600}")
    private long keyTtlSeconds;

    private static final String TOKEN_KEY = "rate:tb:%s:tokens";
    private static final String LAST_KEY  = "rate:tb:%s:last";

    /** Loaded once; the SHA is cached by Redis after the first EVAL. */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> TOKEN_BUCKET_SCRIPT = buildScript();

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> buildScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua")));
        script.setResultType(List.class);
        return script;
    }

    /**
     * Attempt to consume 1 token. Refill + consume happen atomically in Redis.
     */
    @SuppressWarnings("unchecked")
    public RateLimitResult tryConsume(ApiClient client) {
        String apiKey         = client.getApiKey();
        int capacity          = resolve(client.getBucketCapacity(),         defaultCapacity);
        int refillTokens      = resolve(client.getRefillTokens(),           defaultRefillTokens);
        int refillInterval    = resolve(client.getRefillIntervalSeconds(),  defaultRefillIntervalSeconds);

        String tokenKey = String.format(TOKEN_KEY, apiKey);
        String lastKey  = String.format(LAST_KEY, apiKey);
        long now        = Instant.now().getEpochSecond();

        // Single atomic call: refill based on elapsed time, then try to consume.
        List<String> res = redisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                List.of(tokenKey, lastKey),
                String.valueOf(capacity),
                String.valueOf(refillTokens),
                String.valueOf(refillInterval),
                String.valueOf(now),
                String.valueOf(keyTtlSeconds));

        boolean allowed   = "1".equals(res.get(0));
        long remaining    = Long.parseLong(res.get(1));
        long nextRefillIn = Long.parseLong(res.get(2));
        int usagePercent  = (int) (((capacity - remaining) * 100.0) / capacity);

        if (allowed) {
            log.debug("TokenBucket ALLOWED: apiKey={}, remaining={}/{}", apiKey, remaining, capacity);
            return RateLimitResult.builder()
                    .allowed(true)
                    .apiKey(apiKey)
                    .clientName(client.getClientName())
                    .algorithm("TOKEN_BUCKET")
                    .remaining(remaining)
                    .limit(capacity)
                    .resetInSeconds(Math.max(0, nextRefillIn))
                    .usagePercent(usagePercent)
                    .message("Request allowed. " + remaining + " tokens remaining.")
                    .build();
        }

        log.warn("TokenBucket REJECTED: apiKey={}, bucket empty, resets in {}s", apiKey, nextRefillIn);
        return RateLimitResult.builder()
                .allowed(false)
                .apiKey(apiKey)
                .clientName(client.getClientName())
                .algorithm("TOKEN_BUCKET")
                .remaining(0)
                .limit(capacity)
                .resetInSeconds(Math.max(0, nextRefillIn))
                .usagePercent(100)
                .message("Rate limit exceeded. Bucket empty. Retry after " + nextRefillIn + "s.")
                .build();
    }

    private int resolve(Integer clientValue, int defaultValue) {
        return (clientValue != null && clientValue > 0) ? clientValue : defaultValue;
    }
}
