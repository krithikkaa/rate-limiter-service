package com.krithika.ratelimiter.service;

import com.krithika.ratelimiter.dto.ApiClientRequest;
import com.krithika.ratelimiter.dto.ApiClientResponse;
import com.krithika.ratelimiter.exception.ApiKeyNotFoundException;
import com.krithika.ratelimiter.model.ApiClient;
import com.krithika.ratelimiter.model.ApiClient.Tier;
import com.krithika.ratelimiter.repository.ApiClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiClientRepository apiClientRepository;

    // Default limits per tier (requests per minute for sliding window /
    // bucket capacity for token bucket)
    private static final Map<Tier, Integer> TIER_LIMITS = Map.of(
            Tier.FREE,       30,
            Tier.BASIC,      60,
            Tier.PRO,        300,
            Tier.ENTERPRISE, 1000,
            Tier.CUSTOM,     0    // uses override fields
    );

    public ApiClientResponse register(ApiClientRequest request) {
        ApiClient client = ApiClient.builder()
                .clientName(request.getClientName())
                .email(request.getEmail())
                .apiKey(generateApiKey())
                .algorithm(request.getAlgorithm())
                .tier(request.getTier())
                // Custom overrides
                .bucketCapacity(request.getBucketCapacity())
                .refillTokens(request.getRefillTokens())
                .refillIntervalSeconds(request.getRefillIntervalSeconds())
                .windowSizeSeconds(request.getWindowSizeSeconds())
                .maxRequestsPerWindow(request.getMaxRequestsPerWindow())
                .active(true)
                .build();

        ApiClient saved = apiClientRepository.save(client);
        log.info("Registered new API client: name={}, tier={}, algorithm={}",
                saved.getClientName(), saved.getTier(), saved.getAlgorithm());
        return toResponse(saved);
    }

    public List<ApiClientResponse> findAll() {
        return apiClientRepository.findAll()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ApiClientResponse findById(String id) {
        return apiClientRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiKeyNotFoundException("Client not found: " + id));
    }

    public ApiClientResponse deactivate(String id) {
        ApiClient client = apiClientRepository.findById(id)
                .orElseThrow(() -> new ApiKeyNotFoundException("Client not found: " + id));
        client.setActive(false);
        return toResponse(apiClientRepository.save(client));
    }

    private ApiClientResponse toResponse(ApiClient c) {
        int effectiveLimit = (c.getTier() == Tier.CUSTOM && c.getMaxRequestsPerWindow() != null)
                ? c.getMaxRequestsPerWindow()
                : TIER_LIMITS.getOrDefault(c.getTier(), 60);

        return ApiClientResponse.builder()
                .id(c.getId())
                .clientName(c.getClientName())
                .apiKey(c.getApiKey())
                .email(c.getEmail())
                .algorithm(c.getAlgorithm().name())
                .tier(c.getTier().name())
                .effectiveLimit(effectiveLimit)
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .build();
    }

    /**
     * Generates a URL-safe, unpredictable API key in the format:
     *   rl_live_{32-char UUID without dashes}
     */
    private String generateApiKey() {
        return "rl_live_" + UUID.randomUUID().toString().replace("-", "");
    }
}
