package com.distributedjobforge.worker_service.kafka;

import com.distributedjobforge.worker_service.domain.JobStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record JobResultMessage (
        String schemaVersion ,
        UUID jobId ,
        String workerId ,
        int attempt ,
        JobStatus status ,
        Instant startedAt ,
        Instant completedAt ,
        Long durationMs ,
        Integer exitCode ,
        String stdout ,
        String stderr ,
        String errorMessage ,
        Map<String, Object> result
)
{}