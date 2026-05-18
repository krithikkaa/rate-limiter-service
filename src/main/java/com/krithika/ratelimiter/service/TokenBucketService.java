package com.krithika.ratelimiter.service;

import com.krithika.ratelimiter.dto.RateLimitResult;
import com.krithika.ratelimiter.model.ApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * TOKEN BUCKET ALGORITHM
 * ──────────────────────
 * Concept:
 *   - A bucket holds tokens up to `capacity`.
 *   - Every request consumes 1 token.
 *   - Tokens refill at a fixed rate (refillTokens every refillIntervalSeconds).
 *   - If the bucket is empty → request is rejected (429).
 *
 * Why interviewers love this question:
 *   - Allows bursting (e.g., 10 quick requests then waits)
 *   - Predictable memory footprint (2 Redis keys per client)
 *   - Used by AWS API Gateway, Stripe, GitHub
 *
 * Redis keys used:
 *   rate:tb:{apiKey}:tokens   → current token count  (String)
 *   rate:tb:{apiKey}:last     → last refill timestamp (String / epoch seconds)
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

    private static final String TOKEN_KEY   = "rate:tb:%s:tokens";
    private static final String LAST_KEY    = "rate:tb:%s:last";

    /**
     * Attempt to consume 1 token from the bucket.
     * Refills the bucket first based on elapsed time since last refill.
     */
    public RateLimitResult tryConsume(ApiClient client) {
        String apiKey     = client.getApiKey();
        int capacity      = resolve(client.getBucketCapacity(), defaultCapacity);
        int refillTokens  = resolve(client.getRefillTokens(), defaultRefillTokens);
        int refillInterval= resolve(client.getRefillIntervalSeconds(), defaultRefillIntervalSeconds);

        String tokenKey   = String.format(TOKEN_KEY, apiKey);
        String lastKey    = String.format(LAST_KEY, apiKey);

        long now = Instant.now().getEpochSecond();

        // ── Initialise bucket if this is the first request ─────────────────
        Boolean tokenKeyExists = redisTemplate.hasKey(tokenKey);
        if (Boolean.FALSE.equals(tokenKeyExists)) {
            redisTemplate.opsForValue().set(tokenKey, String.valueOf(capacity), keyTtlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(lastKey,  String.valueOf(now),      keyTtlSeconds, TimeUnit.SECONDS);
        }

        // ── Refill based on elapsed time ───────────────────────────────────
        String lastStr   = redisTemplate.opsForValue().get(lastKey);
        long lastRefill  = lastStr != null ? Long.parseLong(lastStr) : now;
        long elapsed     = now - lastRefill;
        long intervals   = elapsed / refillInterval;

        if (intervals > 0) {
            String curStr    = redisTemplate.opsForValue().get(tokenKey);
            long currentTokens = curStr != null ? Long.parseLong(curStr) : 0;
            long newTokens     = Math.min(capacity, currentTokens + (intervals * refillTokens));
            long newLastRefill = lastRefill + (intervals * refillInterval);

            redisTemplate.opsForValue().set(tokenKey, String.valueOf(newTokens),   keyTtlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(lastKey,  String.valueOf(newLastRefill), keyTtlSeconds, TimeUnit.SECONDS);

            log.debug("TokenBucket refill: apiKey={}, added={}, newTotal={}", apiKey, (intervals * refillTokens), newTokens);
        }

        // ── Try to consume 1 token ─────────────────────────────────────────
        String curStr      = redisTemplate.opsForValue().get(tokenKey);
        long currentTokens = curStr != null ? Long.parseLong(curStr) : 0;

        long nextRefillIn  = refillInterval - (now - Long.parseLong(
                redisTemplate.opsForValue().get(lastKey) != null
                ? redisTemplate.opsForValue().get(lastKey) : String.valueOf(now)));

        if (currentTokens > 0) {
            redisTemplate.opsForValue().set(tokenKey, String.valueOf(currentTokens - 1), keyTtlSeconds, TimeUnit.SECONDS);
            long remaining    = currentTokens - 1;
            int usagePercent  = (int) (((capacity - remaining) * 100.0) / capacity);

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
        } else {
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
    }

    private int resolve(Integer clientValue, int defaultValue) {
        return (clientValue != null && clientValue > 0) ? clientValue : defaultValue;
    }
}
