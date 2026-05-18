package com.krithika.ratelimiter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Demo endpoints — these exist purely to demonstrate the rate limiter in action.
 * Hit them with your API key and watch the X-RateLimit-* headers fill in.
 */
@RestController
@RequestMapping("/api/v1/demo")
@Tag(name = "Demo", description = "Test endpoints to observe rate limiting in action")
public class DemoController {

    @GetMapping("/ping")
    @Operation(summary = "Simple ping — watch X-RateLimit-* headers")
    public ResponseEntity<Map<String, Object>> ping(
            @Parameter(hidden = true) @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        return ResponseEntity.ok(Map.of(
                "status",    "OK",
                "message",   "pong",
                "timestamp", LocalDateTime.now().toString(),
                "hint",      "Check response headers for X-RateLimit-Remaining, X-RateLimit-Reset"
        ));
    }

    @GetMapping("/data")
    @Operation(summary = "Simulate a data fetch — hit rapidly to trigger 429")
    public ResponseEntity<Map<String, Object>> data() {
        return ResponseEntity.ok(Map.of(
                "data",      "Here is your data payload",
                "records",   42,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @PostMapping("/submit")
    @Operation(summary = "Simulate a write operation — stricter limits on writes")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "status",  "submitted",
                "payload", body != null ? body : Map.of(),
                "id",      java.util.UUID.randomUUID().toString()
        ));
    }
}
