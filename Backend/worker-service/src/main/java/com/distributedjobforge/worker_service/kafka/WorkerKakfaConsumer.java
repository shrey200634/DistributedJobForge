package com.distributedjobforge.worker_service.kafka;


import com.distributedjobforge.worker_service.domain.JobType;
import com.distributedjobforge.worker_service.executor.ExecutionResult;
import com.distributedjobforge.worker_service.executor.HttpExecutor;
import com.distributedjobforge.worker_service.executor.ShellExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkerKakfaConsumer {

    private final ShellExecutor shellExecutor;
    private final HttpExecutor httpExecutor;
    private final ResultPublisher resultPublisher;

    @Value("${worker.id:worker-local}")
    private String workerId;

    @KafkaListener(topics = "job.pending", groupId = "djf-workers")
    public void onJobPending(JobPendingMessage message, Acknowledgment ack) {
        log.info("Received job: jobId={}, type={}, attempt={}, priority={}",
                message.jobId(), message.type(), message.attempt(), message.priority());

        try {
            ExecutionResult result = executeJob(message);

            JobResultMessage resultMessage = new JobResultMessage(
                    "1.0",
                    message.jobId(),
                    workerId,
                    message.attempt(),
                    result.status(),
                    result.startedAt(),
                    result.completedAt(),
                    result.durationMs(),
                    result.exitCode(),
                    result.stdout(),
                    result.stderr(),
                    result.errorMessage(),
                    Map.of()
            );

            resultPublisher.publishResult(resultMessage);

            // Manually ack only after the result is published
            ack.acknowledge();
            log.info("Job {} processed and acknowledged", message.jobId());

        } catch (Exception e) {
            log.error("Unexpected error processing job {}: {}",
                    message.jobId(), e.getMessage(), e);

            // Publish a FAILED result so api-service still gets notified
            JobResultMessage errorResult = new JobResultMessage(
                    "1.0",
                    message.jobId(),
                    workerId,
                    message.attempt(),
                    com.distributedjobforge.worker_service.domain.JobStatus.FAILED,
                    Instant.now(),
                    Instant.now(),
                    0L,
                    -1,
                    "",
                    e.getMessage(),
                    "Worker error: " + e.getMessage(),
                    Map.of()
            );
            resultPublisher.publishResult(errorResult);
            ack.acknowledge();
        }
    }

    private ExecutionResult executeJob(JobPendingMessage message) {
        if (message.type() == JobType.SHELL) {
            return shellExecutor.execute(
                    message.jobId(),
                    message.payload(),
                    message.timeoutS()
            );
        } else if (message.type() == JobType.HTTP) {
            return httpExecutor.execute(
                    message.jobId(),
                    message.payload(),
                    message.timeoutS()
            );
        }
        // JAVA_CLASS executor comes next (Phase 2 Step 4)
        throw new UnsupportedOperationException(
                "Executor not yet implemented for type: " + message.type()
        );
    }
}
