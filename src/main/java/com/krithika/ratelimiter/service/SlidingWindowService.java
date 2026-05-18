package com.krithika.ratelimiter.service;

import com.krithika.ratelimiter.dto.RateLimitResult;
import com.krithika.ratelimiter.model.ApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * SLIDING WINDOW ALGORITHM
 * ────────────────────────
 * Concept:
 *   - Track timestamps of each request in a Redis sorted set (score = epoch ms).
 *   - On each request: remove entries older than (now - windowSize), then count.
 *   - If count < maxRequests → allow and add current timestamp.
 *   - If count >= maxRequests → reject with 429.
 *
 * Why this beats a fixed window:
 *   - No burst at window boundaries (a common exploit of fixed window counters).
 *   - Truly fair: always looks at the last N seconds regardless of clock alignment.
 *   - Used by Redis itself, Cloudflare, Nginx rate limiting.
 *
 * Redis key:
 *   rate:sw:{apiKey}   → Sorted Set  (member = unique request ID, score = epoch ms)
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

    /**
     * Check and record a request using sliding window.
     */
    public RateLimitResult tryConsume(ApiClient client) {
        String apiKey     = client.getApiKey();
        int windowSeconds = resolve(client.getWindowSizeSeconds(), defaultWindowSizeSeconds);
        int maxRequests   = resolve(client.getMaxRequestsPerWindow(), defaultMaxRequests);

        String key        = String.format(WINDOW_KEY, apiKey);
        long nowMs        = Instant.now().toEpochMilli();
        long windowStartMs= nowMs - (windowSeconds * 1000L);

        ZSetOperations<String, String> zset = redisTemplate.opsForZSet();

        // ── Remove timestamps outside the window ───────────────────────────
        zset.removeRangeByScore(key, 0, windowStartMs);

        // ── Count requests within current window ───────────────────────────
        Long currentCount = zset.zCard(key);
        long count        = currentCount != null ? currentCount : 0;

        long remaining    = Math.max(0, maxRequests - count);
        long resetInMs    = windowSeconds * 1000L;  // simplistic: full window reset
        long resetInSec   = windowSeconds;
        int usagePercent  = (int) ((count * 100.0) / maxRequests);

        if (count < maxRequests) {
            // ── Add this request to the sorted set ─────────────────────────
            // Use "timestamp-nanopart" as member to ensure uniqueness under high concurrency
            String member = nowMs + "-" + Thread.currentThread().getId() + "-" + Math.random();
            zset.add(key, member, nowMs);
            redisTemplate.expire(key, keyTtlSeconds, TimeUnit.SECONDS);

            long newRemaining = remaining - 1;
            log.debug("SlidingWindow ALLOWED: apiKey={}, count={}/{}", apiKey, count + 1, maxRequests);

            return RateLimitResult.builder()
                    .allowed(true)
                    .apiKey(apiKey)
                    .clientName(client.getClientName())
                    .algorithm("SLIDING_WINDOW")
                    .remaining(newRemaining)
                    .limit(maxRequests)
                    .resetInSeconds(resetInSec)
                    .usagePercent(usagePercent)
                    .message("Request allowed. " + newRemaining + " requests remaining in window.")
                    .build();
        } else {
            // ── Find how soon the oldest request will fall out ─────────────
            var oldestEntry = zset.rangeWithScores(key, 0, 0);
            long retryAfterSec = windowSeconds;
            if (oldestEntry != null && !oldestEntry.isEmpty()) {
                double oldestScore = oldestEntry.iterator().next().getScore();
                long oldestMs      = (long) oldestScore;
                retryAfterSec      = Math.max(1, (windowSeconds * 1000L - (nowMs - oldestMs)) / 1000);
            }

            log.warn("SlidingWindow REJECTED: apiKey={}, count={}/{}, retryAfter={}s",
                    apiKey, count, maxRequests, retryAfterSec);

            return RateLimitResult.builder()
                    .allowed(false)
                    .apiKey(apiKey)
                    .clientName(client.getClientName())
                    .algorithm("SLIDING_WINDOW")
                    .remaining(0)
                    .limit(maxRequests)
                    .resetInSeconds(retryAfterSec)
                    .usagePercent(100)
                    .message("Rate limit exceeded. Max " + maxRequests + " requests per "
                            + windowSeconds + "s. Retry after " + retryAfterSec + "s.")
                    .build();
        }
    }

    private int resolve(Integer clientValue, int defaultValue) {
        return (clientValue != null && clientValue > 0) ? clientValue : defaultValue;
    }
}
