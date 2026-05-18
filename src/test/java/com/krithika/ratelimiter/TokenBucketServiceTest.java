package com.krithika.ratelimiter;

import com.krithika.ratelimiter.dto.RateLimitResult;
import com.krithika.ratelimiter.model.ApiClient;
import com.krithika.ratelimiter.service.TokenBucketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TokenBucketServiceTest {

    @Autowired TokenBucketService tokenBucketService;
    @Autowired RedisTemplate<String, String> redisTemplate;

    private ApiClient testClient;

    @BeforeEach
    void setUp() {
        testClient = ApiClient.builder()
                .apiKey("test-key-tb-001")
                .clientName("Test Client")
                .algorithm(ApiClient.RateLimitAlgorithm.TOKEN_BUCKET)
                .tier(ApiClient.Tier.CUSTOM)
                .bucketCapacity(5)
                .refillTokens(5)
                .refillIntervalSeconds(60)
                .build();

        // Clear any stale Redis state before each test
        redisTemplate.delete("rate:tb:test-key-tb-001:tokens");
        redisTemplate.delete("rate:tb:test-key-tb-001:last");
    }

    @Test
    @DisplayName("Should allow requests up to bucket capacity")
    void shouldAllowUpToCapacity() {
        for (int i = 0; i < 5; i++) {
            RateLimitResult result = tokenBucketService.tryConsume(testClient);
            assertThat(result.isAllowed()).isTrue();
        }
    }

    @Test
    @DisplayName("Should reject when bucket is empty")
    void shouldRejectWhenBucketEmpty() {
        // Drain the bucket
        for (int i = 0; i < 5; i++) {
            tokenBucketService.tryConsume(testClient);
        }
        // Next request should be rejected
        RateLimitResult result = tokenBucketService.tryConsume(testClient);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemaining()).isEqualTo(0);
        assertThat(result.getMessage()).contains("Rate limit exceeded");
    }

    @Test
    @DisplayName("Response headers values should be correct")
    void shouldReturnCorrectMetadata() {
        RateLimitResult result = tokenBucketService.tryConsume(testClient);
        assertThat(result.getAlgorithm()).isEqualTo("TOKEN_BUCKET");
        assertThat(result.getLimit()).isEqualTo(5);
        assertThat(result.getRemaining()).isEqualTo(4);
        assertThat(result.getClientName()).isEqualTo("Test Client");
    }
}
