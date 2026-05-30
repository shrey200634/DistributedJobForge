package com.distributedjobforge.api_service.scheduler;

import com.distributedjobforge.api_service.domain.Job;
import com.distributedjobforge.api_service.domain.JobStatus;
import com.distributedjobforge.api_service.kafka.JobEventPublisher;
import com.distributedjobforge.api_service.repository.JobRepo;
import com.distributedjobforge.api_service.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetryScheduler {

    private final RedissonClient redissonClient;
    private final JobRepo jobRepo;
    private final JobEventPublisher jobEventPublisher;


    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollDueRetries() {
        RScoredSortedSet<String> schedule =
                redissonClient.getScoredSortedSet(RetryService.RETRY_SCHEDULE_KEY);

        if (schedule.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Collection<String> due = schedule.valueRange(0, true, now, true);

        for (String jobId : due) {
            boolean removed = schedule.remove(jobId);
            if (!removed) {
                continue; // another poll already grabbed it
            }

            try {
                Job job = jobRepo.findById(UUID.fromString(jobId)).orElse(null);
                if (job == null) {
                    log.warn("Retry: job {} not found, skipping", jobId);
                    continue;
                }
                if (job.getStatus() == JobStatus.CANCELLED
                        || job.getStatus() == JobStatus.SUCCEEDED) {
                    log.info("Retry: job {} is {}, skipping re-dispatch", jobId, job.getStatus());
                    continue;
                }

                job.setStatus(JobStatus.QUEUED);
                jobRepo.save(job);

                jobEventPublisher.publishJobPending(job);
                log.info("Retry: re-published job {} to job.pending", jobId);

            } catch (Exception e) {
                log.error("Retry: failed to re-dispatch job {}: {}", jobId, e.getMessage(), e);
            }
        }
    }
}
