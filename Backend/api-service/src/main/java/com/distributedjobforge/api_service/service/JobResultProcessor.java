package com.distributedjobforge.api_service.service;

import com.distributedjobforge.api_service.domain.Job;
import com.distributedjobforge.api_service.domain.JobExecution;
import com.distributedjobforge.api_service.domain.JobStatus;
import com.distributedjobforge.api_service.exception.JobNotFoundException;
import com.distributedjobforge.api_service.kafka.JobEventPublisher;
import com.distributedjobforge.api_service.kafka.JobResultMessage;
import com.distributedjobforge.api_service.repository.JobExecutionRepo;
import com.distributedjobforge.api_service.repository.JobRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobResultProcessor {

    private final JobRepo jobRepo;
    private final JobExecutionRepo jobExecutionRepo;
    private final RetryService retryService;
    private final JobEventPublisher jobEventPublisher;

    @Transactional
    public void processResult(JobResultMessage resultMessage) {
        Job job = jobRepo.findById(resultMessage.jobId())
                .orElseThrow(() -> new JobNotFoundException(resultMessage.jobId()));

        log.info("Processing result for jobId={}, status={}, workerId={}",
                resultMessage.jobId(), resultMessage.status(), resultMessage.workerId());

        // Always write an audit record for THIS execution attempt
        recordExecution(resultMessage);

        // Common fields
        job.setWorkerId(resultMessage.workerId());
        job.setStartedAt(resultMessage.startedAt());
        job.setCompletedAt(resultMessage.completedAt());

        boolean failed = resultMessage.status() == JobStatus.FAILED
                || resultMessage.status() == JobStatus.TIMEOUT;

        if (!failed) {
            // SUCCESS path
            job.setStatus(resultMessage.status());
            job.setRetryCount(resultMessage.attempt());
            storeResultPayload(job, resultMessage);
            jobRepo.save(job);
            log.info("Job {} SUCCEEDED on attempt {}", job.getId(), resultMessage.attempt());
            return;
        }

        // FAILURE path → decide retry vs DLQ
        job.setErrorMessage(resultMessage.errorMessage());

        boolean willRetry = retryService.scheduleRetryIfPossible(
                job.getId().toString(),
                resultMessage.attempt(),
                job.getMaxRetries()
        );

        if (willRetry) {
            job.setStatus(JobStatus.RETRYING);
            job.setRetryCount(resultMessage.attempt() + 1);
            jobRepo.save(job);
            log.info("Job {} marked RETRYING (attempt {} of {})",
                    job.getId(), resultMessage.attempt() + 1, job.getMaxRetries());
        } else {
            // Retries exhausted → DLQ
            job.setStatus(JobStatus.DLQ);
            job.setRetryCount(resultMessage.attempt());
            jobRepo.save(job);

            jobEventPublisher.publishToDlq(
                    job,
                    resultMessage.attempt(),
                    resultMessage.errorMessage()
            );
            log.warn("Job {} sent to DLQ after {} attempts", job.getId(), resultMessage.attempt());
        }
    }

    private void storeResultPayload(Job job, JobResultMessage r) {
        if (r.stdout() != null || r.stderr() != null) {
            job.setResult(java.util.Map.of(
                    "stdout", r.stdout() != null ? r.stdout() : "",
                    "stderr", r.stderr() != null ? r.stderr() : "",
                    "exitCode", r.exitCode() != null ? r.exitCode() : -1,
                    "durationMs", r.durationMs() != null ? r.durationMs() : 0L
            ));
        }
    }

    private void recordExecution(JobResultMessage r) {
        JobExecution execution = JobExecution.builder()
                .jobId(r.jobId())
                .attempt(r.attempt())
                .workerId(r.workerId())
                .startedAt(r.startedAt())
                .endAt(r.completedAt())
                .exitCode(r.exitCode())
                .stdout(r.stdout())
                .stderr(r.stderr())
                .durationMs(r.durationMs())
                .status(r.status())
                .build();
        jobExecutionRepo.save(execution);
    }
}
