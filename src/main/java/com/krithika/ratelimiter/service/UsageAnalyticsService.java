package com.krithika.ratelimiter.service;

import com.krithika.ratelimiter.dto.UsageStatsResponse;
import com.krithika.ratelimiter.dto.UsageStatsResponse.HourlyUsage;
import com.krithika.ratelimiter.repository.ApiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageAnalyticsService {

    private final ApiUsageLogRepository usageLogRepository;

    public UsageStatsResponse getStatsForKey(String apiKey) {
        long total   = usageLogRepository.countByApiKey(apiKey);
        long allowed = usageLogRepository.countByApiKeyAndAllowedTrue(apiKey);
        long blocked = usageLogRepository.countByApiKeyAndAllowedFalse(apiKey);
        double blockRate = total > 0 ? (blocked * 100.0 / total) : 0.0;

        long last1h  = usageLogRepository.countByApiKeyAndCreatedAtAfter(apiKey, LocalDateTime.now().minusHours(1));
        long last24h = usageLogRepository.countByApiKeyAndCreatedAtAfter(apiKey, LocalDateTime.now().minusHours(24));

        // Hourly breakdown for last 24 hours
        List<Object[]> rawHourly = usageLogRepository.findHourlyUsage(apiKey, LocalDateTime.now().minusHours(24));
        List<HourlyUsage> hourly = rawHourly.stream()
                .map(row -> HourlyUsage.builder()
                        .hour(row[0].toString())
                        .count(Long.parseLong(row[1].toString()))
                        .build())
                .collect(Collectors.toList());

        // Get client name from first log entry if available
        String clientName = usageLogRepository.findTop20ByApiKeyOrderByCreatedAtDesc(apiKey)
                .stream().findFirst().map(l -> l.getClientName()).orElse("Unknown");

        return UsageStatsResponse.builder()
                .apiKey(apiKey)
                .clientName(clientName)
                .totalRequests(total)
                .allowedRequests(allowed)
                .blockedRequests(blocked)
                .blockRatePercent(Math.round(blockRate * 100.0) / 100.0)
                .requestsLast1Hour(last1h)
                .requestsLast24Hours(last24h)
                .hourlyBreakdown(hourly)
                .build();
    }

    public List<UsageStatsResponse> getTopClients(int n) {
        List<Object[]> rows = usageLogRepository.findTopClients(LocalDateTime.now().minusHours(24), n);
        return rows.stream()
                .map(row -> UsageStatsResponse.builder()
                        .apiKey(row[0].toString())
                        .clientName(row[1].toString())
                        .totalRequests(Long.parseLong(row[2].toString()))
                        .build())
                .collect(Collectors.toList());
    }
}
