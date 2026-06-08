package com.distributedjobforge.scheduler_service.dto;


import com.distributedjobforge.scheduler_service.domain.Job;
import com.distributedjobforge.scheduler_service.domain.JobStatus;
import com.distributedjobforge.scheduler_service.domain.JobType;

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
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getIdempotencyKey(),
                job.getType(),
                job.getStatus(),
                job.getPriority(),
                job.getTimeoutS(),
                job.getMaxRetries(),
                job.getRetryCount(),
                job.getPayload(),
                job.getDependsOn(),
                job.getWorkerId(),
                job.getErrorMessage(),
                job.getResult(),
                job.getCreatedAt(),
                job.getScheduledAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }
}
