package com.krithika.ratelimiter.service;

import com.krithika.ratelimiter.dto.RateLimitResult;
import com.krithika.ratelimiter.exception.ApiKeyNotFoundException;
import com.krithika.ratelimiter.model.ApiClient;
import com.krithika.ratelimiter.model.ApiUsageLog;
import com.krithika.ratelimiter.model.RateLimitAlert;
import com.krithika.ratelimiter.repository.ApiClientRepository;
import com.krithika.ratelimiter.repository.ApiUsageLogRepository;
import com.krithika.ratelimiter.repository.RateLimitAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orchestrates:
 *  1. API key lookup (Supabase)
 *  2. Route to correct algorithm (Token Bucket or Sliding Window)
 *  3. Async: persist usage log to Supabase
 *  4. Async: fire alert if usage >= 80% or 100%
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final ApiClientRepository    apiClientRepository;
    private final ApiUsageLogRepository  usageLogRepository;
    private final RateLimitAlertRepository alertRepository;
    private final TokenBucketService     tokenBucketService;
    private final SlidingWindowService   slidingWindowService;

    @Value("${rate-limiter.alert.warning-threshold-percent:80}")
    private int warningThresholdPercent;

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
            case TOKEN_BUCKET    -> tokenBucketService.tryConsume(client);
            case SLIDING_WINDOW  -> slidingWindowService.tryConsume(client);
        };

        // 3. Persist log (async — doesn't block the response)
        persistUsageLog(result, endpoint, method, ip);

        // 4. Check thresholds and fire alerts (async)
        checkAndFireAlerts(client, result);

        return result;
    }

    @Async
    protected void persistUsageLog(RateLimitResult result, String endpoint, String method, String ip) {
        try {
            ApiUsageLog log = ApiUsageLog.builder()
                    .apiKey(result.getApiKey())
                    .clientName(result.getClientName())
                    .endpoint(endpoint)
                    .httpMethod(method)
                    .allowed(result.isAllowed())
                    .algorithm(result.getAlgorithm())
                    .remaining(result.getRemaining())
                    .limitValue(result.getLimit())
                    .responseStatus(result.isAllowed() ? 200 : 429)
                    .ipAddress(ip)
                    .build();
            usageLogRepository.save(log);
        } catch (Exception e) {
            // Never let analytics failure affect the main rate-limit response
            log.error("Failed to persist usage log for apiKey={}: {}", result.getApiKey(), e.getMessage());
        }
    }

    @Async
    protected void checkAndFireAlerts(ApiClient client, RateLimitResult result) {
        try {
            int usagePercent = result.getUsagePercent();

            if (!result.isAllowed()) {
                // 100% — limit exceeded
                RateLimitAlert alert = RateLimitAlert.builder()
                        .apiKey(client.getApiKey())
                        .clientName(client.getClientName())
                        .alertType(RateLimitAlert.AlertType.EXCEEDED)
                        .usagePercent(100)
                        .requestsMade(result.getLimit() - result.getRemaining())
                        .limitValue(result.getLimit())
                        .build();
                alertRepository.save(alert);
                log.warn("ALERT[EXCEEDED] saved for apiKey={}", client.getApiKey());

            } else if (usagePercent >= warningThresholdPercent) {
                // 80%+ warning
                RateLimitAlert alert = RateLimitAlert.builder()
                        .apiKey(client.getApiKey())
                        .clientName(client.getClientName())
                        .alertType(RateLimitAlert.AlertType.WARNING)
                        .usagePercent(usagePercent)
                        .requestsMade(result.getLimit() - result.getRemaining())
                        .limitValue(result.getLimit())
                        .build();
                alertRepository.save(alert);
                log.warn("ALERT[WARNING] saved for apiKey={}, usage={}%", client.getApiKey(), usagePercent);
            }
        } catch (Exception e) {
            log.error("Failed to create alert for apiKey={}: {}", client.getApiKey(), e.getMessage());
        }
    }
}
