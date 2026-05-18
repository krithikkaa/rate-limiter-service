package com.krithika.ratelimiter.controller;

import com.krithika.ratelimiter.dto.UsageStatsResponse;
import com.krithika.ratelimiter.model.RateLimitAlert;
import com.krithika.ratelimiter.repository.RateLimitAlertRepository;
import com.krithika.ratelimiter.service.UsageAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Usage statistics and alerts")
public class AnalyticsController {

    private final UsageAnalyticsService analyticsService;
    private final RateLimitAlertRepository alertRepository;

    @GetMapping("/usage/{apiKey}")
    @Operation(summary = "Get usage stats for a specific API key")
    public ResponseEntity<UsageStatsResponse> getUsageByKey(@PathVariable String apiKey) {
        return ResponseEntity.ok(analyticsService.getStatsForKey(apiKey));
    }

    @GetMapping("/top-clients")
    @Operation(summary = "Top N clients by request volume in last 24h")
    public ResponseEntity<List<UsageStatsResponse>> topClients(
            @RequestParam(defaultValue = "10") int n) {
        return ResponseEntity.ok(analyticsService.getTopClients(n));
    }

    @GetMapping("/alerts")
    @Operation(summary = "List all unacknowledged rate-limit alerts")
    public ResponseEntity<List<RateLimitAlert>> getAlerts() {
        return ResponseEntity.ok(alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc());
    }

    @PatchMapping("/alerts/{id}/acknowledge")
    @Operation(summary = "Acknowledge a rate-limit alert")
    public ResponseEntity<Map<String, String>> acknowledge(@PathVariable String id) {
        alertRepository.findById(id).ifPresent(alert -> {
            alert.setAcknowledged(true);
            alertRepository.save(alert);
        });
        return ResponseEntity.ok(Map.of("status", "acknowledged", "id", id));
    }
}
