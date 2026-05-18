package com.krithika.ratelimiter.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Fired when a client hits 80% or 100% of their rate limit.
 * Stored in Supabase (PostgreSQL).
 */
@Entity
@Table(name = "rate_limit_alerts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RateLimitAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(name = "client_name")
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

    @Column(name = "usage_percent")
    private Integer usagePercent;

    @Column(name = "requests_made")
    private Long requestsMade;

    @Column(name = "limit_value")
    private Long limitValue;

    @Column(name = "acknowledged", nullable = false)
    @Builder.Default
    private Boolean acknowledged = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum AlertType {
        WARNING,   // 80% threshold
        EXCEEDED   // 100% - limit hit
    }
}
