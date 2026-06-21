package com.krithika.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.krithika.ratelimiter.dto.ApiErrorResponse;
import com.krithika.ratelimiter.dto.RateLimitResult;
import com.krithika.ratelimiter.exception.ApiKeyNotFoundException;
import com.krithika.ratelimiter.service.RateLimiterService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RateLimitFilter intercepts every HTTP request.
 *
 * Flow:
 *  1. Extract X-API-Key header
 *  2. Skip filter for public/health endpoints
 *  3. Call RateLimiterService.checkLimit()
 *  4. If allowed → add X-RateLimit-* headers and continue
 *  5. If denied → return 429 JSON immediately
 *  6. If no API key or invalid → return 401 JSON
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements Filter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // These paths bypass rate limiting (Swagger, health, registration)
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/swagger-ui",
            "/api-docs",
            "/actuator",
            "/api/v1/clients"    // allow client registration without key
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path   = httpReq.getRequestURI();
        String method = httpReq.getMethod();

        // ── Skip for public endpoints ──────────────────────────────────────
        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // ── Extract API key ────────────────────────────────────────────────
        String apiKey = httpReq.getHeader("X-API-Key");

        if (apiKey == null || apiKey.isBlank()) {
            writeError(httpRes, 401, "Unauthorized",
                    "Missing X-API-Key header. Register at POST /api/v1/clients to get your key.", path);
            return;
        }

        // ── Run rate limit check ───────────────────────────────────────────
        try {
            String ip = getClientIp(httpReq);
            RateLimitResult result = rateLimiterService.checkLimit(apiKey, path, method, ip);

            // Add standard rate-limit response headers
            httpRes.setHeader("X-RateLimit-Limit",     String.valueOf(result.getLimit()));
            httpRes.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
            httpRes.setHeader("X-RateLimit-Reset",     String.valueOf(result.getResetInSeconds()));
            httpRes.setHeader("X-RateLimit-Algorithm", result.getAlgorithm());
            httpRes.setHeader("X-RateLimit-Usage-Pct", result.getUsagePercent() + "%");

            if (!result.isAllowed()) {
                httpRes.setHeader("Retry-After", String.valueOf(result.getResetInSeconds()));
                writeError(httpRes, 429, "Too Many Requests", result.getMessage(), path);
                return;
            }

            chain.doFilter(request, response);

        } catch (ApiKeyNotFoundException ex) {
            writeError(httpRes, 401, "Unauthorized", ex.getMessage(), path);
        } catch (Exception ex) {
            log.error("RateLimitFilter unexpected error: {}", ex.getMessage(), ex);
            writeError(httpRes, 500, "Internal Server Error",
                    "Rate limiter encountered an unexpected error.", path);
        }
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
    }

    private void writeError(HttpServletResponse response, int status, String error,
                            String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
