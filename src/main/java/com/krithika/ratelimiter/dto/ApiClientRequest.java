package com.krithika.ratelimiter.dto;

import com.krithika.ratelimiter.model.ApiClient.RateLimitAlgorithm;
import com.krithika.ratelimiter.model.ApiClient.Tier;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiClientRequest {

    @NotBlank(message = "Client name is required")
    private String clientName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email")
    private String email;

    @NotNull(message = "Algorithm is required")
    private RateLimitAlgorithm algorithm;

    @NotNull(message = "Tier is required")
    private Tier tier;

    // ── Optional overrides (used when tier = CUSTOM) ─────────────────────────
    private Integer bucketCapacity;
    private Integer refillTokens;
    private Integer refillIntervalSeconds;
    private Integer windowSizeSeconds;
    private Integer maxRequestsPerWindow;
}
