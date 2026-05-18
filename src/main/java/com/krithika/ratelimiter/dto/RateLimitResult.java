package com.krithika.ratelimiter.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RateLimitResult {
    private boolean allowed;
    private String apiKey;
    private String clientName;
    private String algorithm;
    private long remaining;
    private long limit;
    private long resetInSeconds;
    private int usagePercent;
    private String message;
}
