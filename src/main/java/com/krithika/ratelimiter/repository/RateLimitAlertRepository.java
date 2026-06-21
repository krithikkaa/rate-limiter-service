package com.krithika.ratelimiter.repository;

import com.krithika.ratelimiter.model.RateLimitAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RateLimitAlertRepository extends JpaRepository<RateLimitAlert, String> {
    List<RateLimitAlert> findByAcknowledgedFalseOrderByCreatedAtDesc();
    List<RateLimitAlert> findByApiKeyOrderByCreatedAtDesc(String apiKey);
}
