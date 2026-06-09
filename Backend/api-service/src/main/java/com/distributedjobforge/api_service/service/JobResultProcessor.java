package com.distributedjobforge.api_service.service;

import com.distributedjobforge.api_service.domain.Job;
import com.distributedjobforge.api_service.domain.JobExecution;
import com.distributedjobforge.api_service.domain.JobStatus;
import com.distributedjobforge.api_service.exception.JobNotFoundException;
import com.distributedjobforge.api_service.kafka.JobEventPublisher;
import com.distributedjobforge.api_service.kafka.JobResultMessage;
import com.distributedjobforge.api_service.repository.JobExecutionRepo;
import com.distributedjobforge.api_service.repository.JobRepo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;


    @Transactional("transactionManager")
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
            kafkaTemplate.send("job.completed", job.getId().toString(), job.getId().toString());
            log.info("Published job.completed event for jobId={}", job.getId());
            meterRegistry.counter("djf.jobs.completed").increment();
            return;
        }

        // FAILURE path → decide retry vs DLQ
        job.setErrorMessage(resultMessage.errorMessage());

        RetryService.RetryDecision decision = retryService.scheduleRetryIfPossible(
                job.getId().toString(),
                resultMessage.attempt(),
                job.getMaxRetries()
        );

        // The Redis counter is authoritative — mirror it into the DB so the
        // API response reflects the true attempt number.
        int authoritativeAttempt = (int) decision.attempt();

        if (decision.willRetry()) {
            job.setStatus(JobStatus.RETRYING);
            job.setRetryCount(authoritativeAttempt);
            jobRepo.save(job);
            log.info("Job {} marked RETRYING (attempt {} of {})",
                    job.getId(), authoritativeAttempt, job.getMaxRetries());
        } else {
            // Retries exhausted → DLQ
            job.setStatus(JobStatus.DLQ);
            job.setRetryCount(authoritativeAttempt);
            jobRepo.save(job);

            jobEventPublisher.publishToDlq(
                    job,
                    authoritativeAttempt,
                    resultMessage.errorMessage()
            );
            meterRegistry.counter("djf.dlq.size").increment();
            log.warn("Job {} sent to DLQ after {} attempts", job.getId(), authoritativeAttempt);
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
