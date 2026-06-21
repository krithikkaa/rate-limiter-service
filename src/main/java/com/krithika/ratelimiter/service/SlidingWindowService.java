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
import java.util.UUID;

/**
 * SLIDING WINDOW ALGORITHM (request log)
 * ──────────────────────────────────────
 * Concept:
 *   - Track each request's timestamp in a Redis sorted set (score = epoch ms).
 *   - On each request: evict entries older than (now - windowSize), then count.
 *   - If count < maxRequests → admit and record; else reject with 429.
 *
 * Why this beats a fixed window:
 *   - No boundary burst. A fixed window resets its counter at a wall-clock edge,
 *     so a client can fire `max` requests just before the edge and `max` again
 *     just after — 2x the limit across a few milliseconds. The sliding log always
 *     measures the trailing N seconds, so that exploit disappears.
 *
 * Atomicity:
 *   Evict + count + admit run as one Lua script (sliding_window.lua) so two
 *   concurrent requests can't both read the same count and both get admitted.
 *
 * Redis key:
 *   rate:sw:{apiKey}  → Sorted Set (member = unique request id, score = epoch ms)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlidingWindowService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rate-limiter.sliding-window.window-size-seconds:60}")
    private int defaultWindowSizeSeconds;

    @Value("${rate-limiter.sliding-window.max-requests:60}")
    private int defaultMaxRequests;

    @Value("${rate-limiter.alert.redis-key-ttl-seconds:3600}")
    private long keyTtlSeconds;

    private static final String WINDOW_KEY = "rate:sw:%s";

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SLIDING_WINDOW_SCRIPT = buildScript();

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> buildScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/sliding_window.lua")));
        script.setResultType(List.class);
        return script;
    }

    /**
     * Check and record a request using a sliding window log — atomically.
     */
    @SuppressWarnings("unchecked")
    public RateLimitResult tryConsume(ApiClient client) {
        String apiKey     = client.getApiKey();
        int windowSeconds = resolve(client.getWindowSizeSeconds(),     defaultWindowSizeSeconds);
        int maxRequests   = resolve(client.getMaxRequestsPerWindow(),  defaultMaxRequests);

        String key   = String.format(WINDOW_KEY, apiKey);
        long nowMs   = Instant.now().toEpochMilli();
        long windowMs = windowSeconds * 1000L;
        // Unique member so two requests in the same millisecond don't collide.
        String member = nowMs + "-" + UUID.randomUUID();

        List<String> res = redisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                List.of(key),
                String.valueOf(nowMs),
                String.valueOf(windowMs),
                String.valueOf(maxRequests),
                String.valueOf(keyTtlSeconds),
                member);

        boolean allowed   = "1".equals(res.get(0));
        long remaining    = Long.parseLong(res.get(1));
        long retryAfter   = Long.parseLong(res.get(2));
        int usagePercent  = (int) (((maxRequests - remaining) * 100.0) / maxRequests);

        if (allowed) {
            log.debug("SlidingWindow ALLOWED: apiKey={}, remaining={}/{}", apiKey, remaining, maxRequests);
            return RateLimitResult.builder()
                    .allowed(true)
                    .apiKey(apiKey)
                    .clientName(client.getClientName())
                    .algorithm("SLIDING_WINDOW")
                    .remaining(remaining)
                    .limit(maxRequests)
                    .resetInSeconds(windowSeconds)
                    .usagePercent(usagePercent)
                    .message("Request allowed. " + remaining + " requests remaining in window.")
                    .build();
        }

        log.warn("SlidingWindow REJECTED: apiKey={}, limit={}/{}s, retryAfter={}s",
                apiKey, maxRequests, windowSeconds, retryAfter);
        return RateLimitResult.builder()
                .allowed(false)
                .apiKey(apiKey)
                .clientName(client.getClientName())
                .algorithm("SLIDING_WINDOW")
                .remaining(0)
                .limit(maxRequests)
                .resetInSeconds(retryAfter)
                .usagePercent(100)
                .message("Rate limit exceeded. Max " + maxRequests + " requests per "
                        + windowSeconds + "s. Retry after " + retryAfter + "s.")
                .build();
    }

    private int resolve(Integer clientValue, int defaultValue) {
        return (clientValue != null && clientValue > 0) ? clientValue : defaultValue;
    }
}
