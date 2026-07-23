package com.example.jobscheduler.dto;

import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public record CreateScheduleRequest(
        @NotBlank @Size(max = 100) String jobType,
        @NotNull JsonNode payload,
        @NotBlank @Size(max = 100) String cronExpression,
        @NotBlank @Size(max = 80) String timezone,
        @Min(0) @Max(20) Integer maxRetries,
        @Min(1) @Max(86400) Integer timeoutSeconds
) {
}
