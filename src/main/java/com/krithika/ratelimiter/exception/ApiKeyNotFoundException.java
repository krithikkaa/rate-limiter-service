package com.krithika.ratelimiter.exception;

public class ApiKeyNotFoundException extends RuntimeException {
    public ApiKeyNotFoundException(String message) {
        super(message);
    }
}
