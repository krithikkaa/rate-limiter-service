package com.krithika.ratelimiter.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiClientResponse {
    private String id;
    private String clientName;
    private String apiKey;
    private String email;
    private String algorithm;
    private String tier;
    private Integer effectiveLimit;
    private Boolean active;
    private LocalDateTime createdAt;
}
