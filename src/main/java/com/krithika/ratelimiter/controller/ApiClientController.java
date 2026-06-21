package com.krithika.ratelimiter.controller;

import com.krithika.ratelimiter.dto.ApiClientRequest;
import com.krithika.ratelimiter.dto.ApiClientResponse;
import com.krithika.ratelimiter.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "API Client Management", description = "Register and manage API clients")
public class ApiClientController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @Operation(summary = "Register a new API client",
               description = "Creates an API key for a new client. No auth required.")
    public ResponseEntity<ApiClientResponse> register(@Valid @RequestBody ApiClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.register(request));
    }

    @GetMapping
    @Operation(summary = "List all registered clients")
    public ResponseEntity<List<ApiClientResponse>> listAll() {
        return ResponseEntity.ok(apiKeyService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a client by ID")
    public ResponseEntity<ApiClientResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(apiKeyService.findById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a client (revoke API key)")
    public ResponseEntity<ApiClientResponse> deactivate(@PathVariable String id) {
        return ResponseEntity.ok(apiKeyService.deactivate(id));
    }
}
