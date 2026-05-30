package com.distributedjobforge.api_service.kafka;

import com.distributedjobforge.api_service.domain.JobStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobResultMessage (
        String schemaVersion ,
        UUID jobId ,
        String workerID ,
        int attempt ,
        JobStatus status ,
        Instant startedAt ,
        Instant completedAt ,
        Long durationMs ,
        Integer exitCode ,
        String stdout ,
        String stderr ,
        String errorMessage ,
        Map<String , Object> result
){}