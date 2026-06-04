package com.distributedjobforge.api_service.scheduler;
import com.distributedjobforge.api_service.domain.Job;
import com.distributedjobforge.api_service.domain.JobStatus;
import com.distributedjobforge.api_service.kafka.JobEventPublisher;
import com.distributedjobforge.api_service.repository.JobRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadWorkerWatchdog {

    private final RedissonClient redissonClient;
    private final JobRepo jobRepo;
    private final JobEventPublisher jobEventPublisher;

    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void checkForDeadWorkers() {

        Set<Object> activeWorkers = redissonClient
                .<Object>getSet("workers:active").readAll();
        if (activeWorkers.isEmpty()) return;
        for (Object workerIdObj : activeWorkers) {
            String workerId = workerIdObj.toString();
            boolean alive = redissonClient.getBucket("workers:" + workerId).isExists();
            if (alive) continue;
            log.warn("Dead worker detected: {} — reassigning its in-progress jobs", workerId);
            Set<Object> inProgressJobIds = redissonClient
                    .<Object>getSet("jobs:in-progress:" + workerId).readAll();

            for (Object jobIdObj : inProgressJobIds) {
                try {
                    UUID jobId = UUID.fromString(jobIdObj.toString());
                    Optional<Job> jobOpt = jobRepo.findById(jobId);

                    if (jobOpt.isEmpty()) {
                        log.warn("In-progress job {} not found in DB, skipping", jobId);
                        continue;
                    }

                    Job job = jobOpt.get();
                    if (job.getStatus() == JobStatus.SUCCEEDED
                            || job.getStatus() == JobStatus.DLQ) {
                        continue;
                    }

                    job.setStatus(JobStatus.PENDING);
                    jobRepo.save(job);
                    jobEventPublisher.publishJobPending(job);
                    log.info("Re-queued job {} from dead worker {}", jobId, workerId);

                } catch (Exception e) {
                    log.error("Error reassigning job {} from dead worker {}: {}",
                            jobIdObj, workerId, e.getMessage());
                }
            }
            redissonClient.getSet("jobs:in-progress:" + workerId).delete();
            redissonClient.getSet("workers:active").remove(workerId);
            log.info("Cleaned up dead worker: {}", workerId);
        }
    }
}