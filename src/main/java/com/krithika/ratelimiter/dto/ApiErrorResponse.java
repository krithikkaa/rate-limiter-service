package com.krithika.ratelimiter.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiErrorResponse {
    private int status;
    private String error;
    private String message;
    private List<String> details;
    private LocalDateTime timestamp;
    private String path;
}
