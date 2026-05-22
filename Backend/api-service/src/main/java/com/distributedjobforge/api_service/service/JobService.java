package com.distributedjobforge.api_service.service;


import com.distributedjobforge.api_service.domain.Job;
import com.distributedjobforge.api_service.domain.JobStatus;
import com.distributedjobforge.api_service.dto.JobResponse;
import com.distributedjobforge.api_service.dto.JobSubmitRequest;
import com.distributedjobforge.api_service.exception.InvalidJobStateException;
import com.distributedjobforge.api_service.exception.JobNotFoundException;
import com.distributedjobforge.api_service.repository.JobRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {
    private final JobRepo repo ;
    private final RedissonClient redissonClient ;

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

}
