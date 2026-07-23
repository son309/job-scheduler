package com.example.jobscheduler.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        String jobType,
        JsonNode payload,
        String cronExpression,
        String timezone,
        int maxRetries,
        int timeoutSeconds,
        boolean enabled,
        Instant nextRunAt,
        Instant lastRunAt,
        Instant createdAt,
        Instant updatedAt
) {
}
