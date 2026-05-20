package com.distributedjobforge.api_service.dto;

import com.distributedjobforge.api_service.domain.JobStatus;
import com.distributedjobforge.api_service.domain.JobType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobResponse(
        UUID jobId,
        String idempotencyKey,
        JobType type,
        JobStatus status,
        int priority,
        int timeoutS,
        int maxRetries,
        int retryCount,
        Map<String, Object> payload,
        List<UUID> dependsOn,
        String workerId,
        String errorMessage,
        Map<String, Object> result,
        Instant createdAt,
        Instant scheduledAt,
        Instant startedAt,
        Instant completedAt
) {}
