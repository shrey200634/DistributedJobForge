package com.distributedjobforge.api_service.kafka;

import com.distributedjobforge.api_service.domain.JobType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobDlqMessage(
        String schemaVersion ,
        UUID jobId ,
        JobType type,
        String idempotencyKey ,
        int totalAttempt ,
        String lastErrorMessage ,
        Map<String , Object> payload ,
        Instant failedAt
){}