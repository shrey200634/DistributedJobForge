package com.distributedjobforge.api_service.service;


import com.distributedjobforge.api_service.domain.Job;
import com.distributedjobforge.api_service.domain.JobStatus;
import com.distributedjobforge.api_service.dto.BatchJobItem;
import com.distributedjobforge.api_service.dto.BatchJobSubmitRequest;
import com.distributedjobforge.api_service.dto.JobResponse;
import com.distributedjobforge.api_service.dto.JobSubmitRequest;
import com.distributedjobforge.api_service.exception.InvalidJobStateException;
import com.distributedjobforge.api_service.exception.JobNotFoundException;
import com.distributedjobforge.api_service.kafka.JobEventPublisher;
import com.distributedjobforge.api_service.repository.JobRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.type.TypeDescription;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

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
    private final DagResolverService dagResolverService;

    @Transactional
    public JobResponse submitJob(JobSubmitRequest request ){

        // key check Idem via redia setnx
        RBucket<String > bucket = redissonClient.getBucket("idem:" + request.idempotencyKey());
        String existingJobId = bucket.get();

        if ( existingJobId !=  null ){
            //Duplicate Submission - return the original job
            log.info("Duplicate idempotency key: {}, returning existing job: {}",
                    request.idempotencyKey() , existingJobId);

            Job existingJob  = repo.findById(UUID.fromString(existingJobId))
                    .orElseThrow(()-> new JobNotFoundException(existingJobId));
            return  JobResponse.from(existingJob);

        }
        // Determine initial status based on dependencies
        JobStatus initialStatus = (request.dependsOn()==null || request.dependsOn().isEmpty())
                ? JobStatus.PENDING
                : JobStatus.BLOCKED;
        // Job entity

        Job job =Job.builder()
                .idempotencyKey(request.idempotencyKey())
                .type(request.type())
                .status(initialStatus)
                .priority(request.priority())
                .payload(request.payload())
                .dependsOn(request.dependsOn())
                .maxRetries(request.maxRetries())
                .timeoutS(request.timeoutS())
                .build();

        job= repo.save(job);

          // now we need to stre the idempodency key in redis with 24 ttl
        bucket.set(job.getId().toString() , 24 , TimeUnit.HOURS);

        if (job.getStatus() == JobStatus.PENDING){
            jobEventPublisher.publishJobPending(job);
        }

        log.info("Job created: id={}, type={}, status={}, priority={}",
                job.getId() , job.getType() , job.getStatus() , job.getPriority());

        return  JobResponse.from(job);

    }
    public  JobResponse getJob (UUID jobId ){
        Job job = repo.findById(jobId)
                .orElseThrow(()-> new JobNotFoundException(jobId));

        return  JobResponse.from( job);

    }
    @Transactional
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


    @Transactional
    public List<JobResponse> submitBatch(BatchJobSubmitRequest request) {
        List<BatchJobItem> sorted = dagResolverService.topologicalSort(request.jobs());
        Map<String, UUID> refToUuid = new LinkedHashMap<>();
        for (BatchJobItem item : sorted) {
            refToUuid.put(item.clientRefId(), UUID.randomUUID());
        }
        List<Job> saved = new ArrayList<>();
        for (BatchJobItem item : sorted) {
            UUID jobId = refToUuid.get(item.clientRefId());

            List<UUID> deps = (item.dependsOn() == null || item.dependsOn().isEmpty())
                    ? new ArrayList<>()
                    : item.dependsOn().stream()
                    .map(refToUuid::get)
                    .collect(Collectors.toList());
            JobStatus status = deps.isEmpty() ? JobStatus.PENDING : JobStatus.BLOCKED;

            Job job = Job.builder()
                    .id(jobId)
                    .type(item.type())
                    .status(status)
                    .priority(item.priority() != null ? item.priority() : 5)
                    .timeoutS(item.timeoutS() != null && item.timeoutS() > 0 ? item.timeoutS() : 60)
                    .maxRetries(item.MaxRetries() != null ? item.MaxRetries() : 3)
                    .payload(item.payload())
                    .dependsOn(deps)
                    .build();

            saved.add(repo.save(job));
        }
        long rootCount = 0;
        for (Job job : saved) {
            if (job.getStatus() == JobStatus.PENDING) {
                jobEventPublisher.publishJobPending(job);
                rootCount++;
            }
        }
        log.info("Batch submitted: {} total jobs, {} root(s) queued immediately",
                saved.size(), rootCount);
        return saved.stream().map(JobResponse::from).collect(Collectors.toList());
    }

}
