package com.krithika.ratelimiter.exception;

import com.krithika.ratelimiter.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiKeyNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ApiKeyNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(404, "Not Found", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.builder()
                        .status(400).error("Bad Request")
                        .message("Validation failed")
                        .details(details)
                        .timestamp(LocalDateTime.now())
                        .path(req.getRequestURI())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(500, "Internal Server Error", ex.getMessage(), req.getRequestURI()));
    }

    private ApiErrorResponse error(int status, String error, String message, String path) {
        return ApiErrorResponse.builder()
                .status(status).error(error).message(message)
                .timestamp(LocalDateTime.now()).path(path).build();
    }
}
