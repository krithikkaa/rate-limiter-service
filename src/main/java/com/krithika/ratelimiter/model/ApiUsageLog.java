package com.krithika.ratelimiter.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persists every API request for analytics dashboards.
 * Stored in Supabase (PostgreSQL).
 */
@Entity
@Table(name = "api_usage_logs",
       indexes = {
           @Index(name = "idx_usage_api_key", columnList = "api_key"),
           @Index(name = "idx_usage_created_at", columnList = "created_at"),
           @Index(name = "idx_usage_allowed", columnList = "allowed")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "allowed", nullable = false)
    private Boolean allowed;

    @Column(name = "algorithm", length = 20)
    private String algorithm;

    /** Remaining tokens / requests at time of this call */
    @Column(name = "remaining")
    private Long remaining;

    /** Full limit the client is configured for */
    @Column(name = "limit_value")
    private Long limitValue;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "ip_address")
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
