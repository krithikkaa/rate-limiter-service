package com.krithika.ratelimiter.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UsageStatsResponse {
    private String apiKey;
    private String clientName;
    private long totalRequests;
    private long allowedRequests;
    private long blockedRequests;
    private double blockRatePercent;
    private long requestsLast1Hour;
    private long requestsLast24Hours;
    private List<HourlyUsage> hourlyBreakdown;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HourlyUsage {
        private String hour;
        private long count;
    }
}
