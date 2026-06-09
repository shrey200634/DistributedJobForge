package com.distributedjobforge.api_service.service;


import com.distributedjobforge.api_service.domain.Job;
import com.distributedjobforge.api_service.domain.JobStatus;
import com.distributedjobforge.api_service.dto.JobResponse;
import com.distributedjobforge.api_service.dto.JobSubmitRequest;
import com.distributedjobforge.api_service.exception.InvalidJobStateException;
import com.distributedjobforge.api_service.exception.JobNotFoundException;
import com.distributedjobforge.api_service.kafka.JobEventPublisher;
import com.distributedjobforge.api_service.repository.JobRepo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {
    private final JobRepo repo ;
    private final RedissonClient redissonClient ;
    private  final JobEventPublisher jobEventPublisher;
    private  final MeterRegistry registry;




    @Transactional("transactionManager")
    public JobResponse submitJob(JobSubmitRequest request) {

        // Idempotency check via Redis setnx
        RBucket<String> bucket = redissonClient.getBucket("idem:" + request.idempotencyKey());
        String existingJobId = bucket.get();

        if (existingJobId != null) {
            log.info("Duplicate idempotency key: {}, returning existing job: {}",
                    request.idempotencyKey(), existingJobId);
            Job existingJob = repo.findById(UUID.fromString(existingJobId))
                    .orElseThrow(() -> new JobNotFoundException(existingJobId));
            return JobResponse.from(existingJob);
        }

        // Determine initial status based on dependencies
        JobStatus initialStatus = (request.dependsOn() == null || request.dependsOn().isEmpty())
                ? JobStatus.PENDING
                : JobStatus.BLOCKED;

        Job job = Job.builder()
                .idempotencyKey(request.idempotencyKey())
                .type(request.type())
                .status(initialStatus)
                .priority(request.priority())
                .payload(request.payload())
                .dependsOn(request.dependsOn())
                .maxRetries(request.maxRetries())
                .timeoutS(request.timeoutS())
                .build();

        job = repo.save(job);

        bucket.set(job.getId().toString(), 24, TimeUnit.HOURS);
        if (job.getStatus() == JobStatus.PENDING) {
            final Job finalJob = job;
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            jobEventPublisher.publishJobPending(finalJob);
                            log.info("Job {} published to job.pending after DB commit", finalJob.getId());
                        }
                    }
            );
        }
        log.info("Job created: id={}, type={}, status={}, priority={}",
                job.getId(), job.getType(), job.getStatus(), job.getPriority());
        return JobResponse.from(job);
    }
    public  JobResponse getJob (UUID jobId ){
        Job job = repo.findById(jobId)
                .orElseThrow(()-> new JobNotFoundException(jobId));
        registry.counter("djf.jobs.submitted").increment();
        return  JobResponse.from( job);
    }


    @Transactional("transactionManager")
    public  JobResponse cancelJob ( UUID jobId ){
        Job job = repo.findById(jobId)
                .orElseThrow(()-> new JobNotFoundException(jobId));

        if ( job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.QUEUED) {
            throw new InvalidJobStateException(" Can only cancel the pending and queued jobs . current status is :" + job.getStatus());
        }
        job.setStatus(JobStatus.CANCELLED);
        job = repo.save(job);
        log.info("job cancelled ;{}" , jobId);

        return JobResponse.from(job);


    }



}
