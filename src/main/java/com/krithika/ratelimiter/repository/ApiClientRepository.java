package com.krithika.ratelimiter.repository;

import com.krithika.ratelimiter.model.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClient, String> {
    Optional<ApiClient> findByApiKeyAndActiveTrue(String apiKey);
    Optional<ApiClient> findByApiKey(String apiKey);
    boolean existsByEmail(String email);
}
