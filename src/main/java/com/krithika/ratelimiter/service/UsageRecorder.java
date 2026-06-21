package com.krithika.ratelimiter.service;

import com.krithika.ratelimiter.dto.RateLimitResult;
import com.krithika.ratelimiter.model.ApiClient;
import com.krithika.ratelimiter.model.ApiUsageLog;
import com.krithika.ratelimiter.model.RateLimitAlert;
import com.krithika.ratelimiter.repository.ApiUsageLogRepository;
import com.krithika.ratelimiter.repository.RateLimitAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Off-thread persistence for usage logs and rate-limit alerts.
 *
 * This is a SEPARATE bean on purpose. @Async is implemented with a Spring proxy,
 * and a proxy is only applied on calls that cross a bean boundary. If these
 * methods lived in RateLimiterService and were called as this.persistUsageLog(),
 * the call would bypass the proxy and run synchronously on the request thread —
 * which is exactly the bug this class exists to avoid. RateLimiterService injects
 * this bean and calls it, so the call goes through the proxy and really runs on
 * the usageExecutor pool, keeping the rate-limit decision off the persistence path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsageRecorder {

    private final ApiUsageLogRepository    usageLogRepository;
    private final RateLimitAlertRepository alertRepository;

    @Value("${rate-limiter.alert.warning-threshold-percent:80}")
    private int warningThresholdPercent;

    /** Persist one usage log row. Never allowed to affect the caller. */
    @Async("usageExecutor")
    public void recordUsage(RateLimitResult result, String endpoint, String method, String ip) {
        try {
            ApiUsageLog entry = ApiUsageLog.builder()
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
            usageLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist usage log for apiKey={}: {}", result.getApiKey(), e.getMessage());
        }
    }

    /** Fire a WARNING (>= threshold) or EXCEEDED (100%) alert, off-thread. */
    @Async("usageExecutor")
    public void evaluateAndAlert(ApiClient client, RateLimitResult result) {
        try {
            int usagePercent = result.getUsagePercent();

            if (!result.isAllowed()) {
                alertRepository.save(RateLimitAlert.builder()
                        .apiKey(client.getApiKey())
                        .clientName(client.getClientName())
                        .alertType(RateLimitAlert.AlertType.EXCEEDED)
                        .usagePercent(100)
                        .requestsMade(result.getLimit() - result.getRemaining())
                        .limitValue(result.getLimit())
                        .build());
                log.warn("ALERT[EXCEEDED] saved for apiKey={}", client.getApiKey());

            } else if (usagePercent >= warningThresholdPercent) {
                alertRepository.save(RateLimitAlert.builder()
                        .apiKey(client.getApiKey())
                        .clientName(client.getClientName())
                        .alertType(RateLimitAlert.AlertType.WARNING)
                        .usagePercent(usagePercent)
                        .requestsMade(result.getLimit() - result.getRemaining())
                        .limitValue(result.getLimit())
                        .build());
                log.warn("ALERT[WARNING] saved for apiKey={}, usage={}%", client.getApiKey(), usagePercent);
            }
        } catch (Exception e) {
            log.error("Failed to create alert for apiKey={}: {}", client.getApiKey(), e.getMessage());
        }
    }
}
