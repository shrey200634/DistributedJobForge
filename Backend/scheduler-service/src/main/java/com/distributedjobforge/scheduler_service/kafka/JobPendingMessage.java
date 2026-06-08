package com.distributedjobforge.scheduler_service.kafka;


import com.distributedjobforge.scheduler_service.domain.Job;
import com.distributedjobforge.scheduler_service.domain.JobType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobPendingMessage (
        String schemaVersion,
        UUID jobId,
        JobType type,
        int priority,
        Map<String, Object> payload,
        int timeoutS,
        int maxRetries,
        int attempt,
        String idempotencyKey,
        Instant enqueuedAt,
        List<String> tags
) {
    public static JobPendingMessage from(Job job) {
        return new JobPendingMessage(
                "1.0",
                job.getId(),
                job.getType(),
                job.getPriority(),
                job.getPayload(),
                job.getTimeoutS(),
                job.getMaxRetries(),
                job.getRetryCount(),
                job.getIdempotencyKey(),
                Instant.now(),
                List.of()
        );
    }
}
