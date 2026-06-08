package com.distributedjobforge.scheduler_service.scheduler;

import com.distributedjobforge.scheduler_service.domain.Job;
import com.distributedjobforge.scheduler_service.domain.JobStatus;
import com.distributedjobforge.scheduler_service.kafka.JobEventPublisher;
import com.distributedjobforge.scheduler_service.repository.JobRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupReconciliationService {

    private final JobRepo jobRepo;
    private final JobEventPublisher jobEventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcile() {
        List<Job> blockedJob = jobRepo.findByStatusWithDependencies(JobStatus.BLOCKED);
        if (blockedJob.isEmpty()) {
            log.info("Startup reconciliation: no BLOCKED jobs found");
            return;
        }

        log.info("Startup reconciliation: checking {} BLOCKED job(s)", blockedJob.size());
        int requeued = 0;

        for (Job job : blockedJob) {
            if (job.getDependsOn() == null || job.getDependsOn().isEmpty()) {
                job.setStatus(JobStatus.PENDING);
                jobRepo.save(job);
                jobEventPublisher.publishJobPending(job);
                requeued++;
                log.warn("Job {} was BLOCKED with no parents — fixed and re-queued", job.getId());
                continue;
            }

            long stillMatching = jobRepo.countUnfinishedParents(
                    job.getDependsOn(), JobStatus.SUCCEEDED
            );

            if (stillMatching == 0) {
                job.setStatus(JobStatus.PENDING);
                jobRepo.save(job);
                jobEventPublisher.publishJobPending(job);
                requeued++;
                log.info("Reconciled stranded job {} — all parents done, re-queued", job.getId());
            }
        }

        log.info("Startup reconciliation complete: {}/{} jobs re-queued",
                requeued, blockedJob.size());
    }
}