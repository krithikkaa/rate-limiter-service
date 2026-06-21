package com.krithika.ratelimiter.service;

import com.krithika.ratelimiter.dto.RateLimitResult;
import com.krithika.ratelimiter.exception.ApiKeyNotFoundException;
import com.krithika.ratelimiter.model.ApiClient;
import com.krithika.ratelimiter.repository.ApiClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a rate-limit decision:
 *  1. API key lookup (Supabase / PostgreSQL)
 *  2. Route to the correct algorithm (Token Bucket or Sliding Window)
 *  3. Hand off usage logging + alerting to UsageRecorder (runs off-thread)
 *
 * Note on the async hand-off: persistence is delegated to UsageRecorder, a
 * separate bean, so its @Async methods are invoked through the Spring proxy and
 * actually execute on the usageExecutor pool. The rate-limit decision returns to
 * the caller without waiting for the database writes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final ApiClientRepository  apiClientRepository;
    private final TokenBucketService   tokenBucketService;
    private final SlidingWindowService slidingWindowService;
    private final UsageRecorder        usageRecorder;

    /**
     * Main entry point called from the servlet filter.
     *
     * @param apiKey   value from X-API-Key header
     * @param endpoint request path (for analytics)
     * @param method   HTTP method
     * @param ip       caller IP
     */
    public RateLimitResult checkLimit(String apiKey, String endpoint, String method, String ip) {

        // 1. Validate API key against Supabase
        ApiClient client = apiClientRepository.findByApiKeyAndActiveTrue(apiKey)
                .orElseThrow(() -> new ApiKeyNotFoundException(
                        "Invalid or inactive API key: " + apiKey));

        // 2. Route to algorithm
        RateLimitResult result = switch (client.getAlgorithm()) {
            case TOKEN_BUCKET   -> tokenBucketService.tryConsume(client);
            case SLIDING_WINDOW -> slidingWindowService.tryConsume(client);
        };

        // 3. Persist log + evaluate alerts off the request thread (via proxy → async pool)
        usageRecorder.recordUsage(result, endpoint, method, ip);
        usageRecorder.evaluateAndAlert(client, result);

        return result;
    }
}
