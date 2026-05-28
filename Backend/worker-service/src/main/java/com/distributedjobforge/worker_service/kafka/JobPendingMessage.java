package com.distributedjobforge.worker_service.kafka;

import com.distributedjobforge.worker_service.domain.JobType;
import org.springframework.kafka.support.serializer.StringOrBytesSerializer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
)
{}
