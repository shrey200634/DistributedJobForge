package com.distributedjobforge.scheduler_service.service;

import com.distributedjobforge.scheduler_service.domain.Job;
import com.distributedjobforge.scheduler_service.domain.JobStatus;
import com.distributedjobforge.scheduler_service.dto.BatchJobItem;
import com.distributedjobforge.scheduler_service.dto.BatchJobSubmitRequest;
import com.distributedjobforge.scheduler_service.dto.JobResponse;
import com.distributedjobforge.scheduler_service.kafka.JobEventPublisher;
import com.distributedjobforge.scheduler_service.repository.JobRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BatchJobService {

    private final JobRepo repo;
    private final JobEventPublisher jobEventPublisher;
    private final DagResolverService dagResolverService;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Transactional
    public List<JobResponse> submitBatch(BatchJobSubmitRequest request) {
        List<BatchJobItem> sorted = dagResolverService.topologicalSort(request.jobs());

        // Pass 1 — persist all jobs without dependencies, let Hibernate generate IDs
        Map<String, Job> savedByRef = new LinkedHashMap<>();
        for (BatchJobItem item : sorted) {
            Job job = Job.builder()
                    .type(item.type())
                    .status(JobStatus.PENDING)
                    .priority(item.priority() != null ? item.priority() : 5)
                    .timeoutS(item.timeoutS() != null && item.timeoutS() > 0 ? item.timeoutS() : 60)
                    .maxRetries(item.MaxRetries() != null ? item.MaxRetries() : 3)
                    .payload(item.payload())
                    .dependsOn(new ArrayList<>())
                    .idempotencyKey(item.idempotencyKey() != null
                            ? item.idempotencyKey()
                            : "batch-" + item.clientRefId() + "-" + UUID.randomUUID())
                    .build();
            savedByRef.put(item.clientRefId(), repo.save(job));
        }

        // Pass 2 — real UUIDs now exist, wire up dependencies and flip to BLOCKED
        for (BatchJobItem item : sorted) {
            if (item.dependsOn() == null || item.dependsOn().isEmpty()) continue;

            Job job = savedByRef.get(item.clientRefId());
            List<UUID> deps = item.dependsOn().stream()
                    .map(ref -> savedByRef.get(ref).getId())
                    .collect(Collectors.toList());
            job.setDependsOn(deps);
            job.setStatus(JobStatus.BLOCKED);
            repo.save(job);
        }

        // Publish only root jobs (still PENDING after pass 2)
        List<Job> toPublish = savedByRef.values().stream()
                .filter(j -> j.getStatus() == JobStatus.PENDING)
                .collect(Collectors.toList());

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        toPublish.forEach(jobEventPublisher::publishJobPending);
                        log.info("Batch: {} total jobs, {} root(s) published after DB commit",
                                savedByRef.size(), toPublish.size());
                    }
                }
        );

        meterRegistry.counter("djf.jobs.submitted").increment(request.jobs().size());

        return savedByRef.values().stream()
                .map(JobResponse::from)
                .collect(Collectors.toList());
    }
}