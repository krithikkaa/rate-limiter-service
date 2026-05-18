package com.krithika.ratelimiter.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a registered API client with its own rate limit tier.
 * Stored in Supabase (PostgreSQL).
 */
@Entity
@Table(name = "api_clients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiClient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(name = "email", nullable = false)
    private String email;

    /** Which algorithm to use for this client */
    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false)
    private RateLimitAlgorithm algorithm;

    /** Tier controls default limits; CUSTOM lets you override them */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false)
    private Tier tier;

    // ── Token Bucket overrides (null = use application.yml defaults) ────────
    @Column(name = "bucket_capacity")
    private Integer bucketCapacity;

    @Column(name = "refill_tokens")
    private Integer refillTokens;

    @Column(name = "refill_interval_seconds")
    private Integer refillIntervalSeconds;

    // ── Sliding Window overrides ─────────────────────────────────────────────
    @Column(name = "window_size_seconds")
    private Integer windowSizeSeconds;

    @Column(name = "max_requests_per_window")
    private Integer maxRequestsPerWindow;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum RateLimitAlgorithm {
        TOKEN_BUCKET,    // burst-friendly
        SLIDING_WINDOW   // strict per-window limit
    }

    public enum Tier {
        FREE,       // 30 req/min
        BASIC,      // 60 req/min
        PRO,        // 300 req/min
        ENTERPRISE, // 1000 req/min
        CUSTOM      // override fields used
    }
}
