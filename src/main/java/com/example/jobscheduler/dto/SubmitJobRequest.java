package com.example.jobscheduler.dto;

import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public record SubmitJobRequest(

        @NotBlank(message = "jobType must not be blank")
        @Size(max = 100, message = "jobType must not exceed 100 characters")
        String jobType,

        @NotNull(message = "payload must not be null")
        JsonNode payload,

        @Min(value = 0, message = "maxRetries must be at least 0")
        @Max(value = 20, message = "maxRetries must not exceed 20")
        Integer maxRetries,

        @Min(value = 1, message = "timeoutSeconds must be at least 1")
        @Max(value = 86400, message = "timeoutSeconds is too large")
        Integer timeoutSeconds,

        @Size(max = 255)
        String idempotencyKey
) {
}
