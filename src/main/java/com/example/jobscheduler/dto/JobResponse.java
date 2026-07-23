package com.example.jobscheduler.dto;

import com.example.jobscheduler.entity.JobStatus;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String jobType,
        JsonNode payload,
        JobStatus status,
        int attemptCount,
        int maxRetries,
        int timeoutSeconds,
        String idempotencyKey,
        JsonNode result,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
