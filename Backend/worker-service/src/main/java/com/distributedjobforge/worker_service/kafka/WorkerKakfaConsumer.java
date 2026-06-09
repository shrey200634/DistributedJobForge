package com.distributedjobforge.worker_service.kafka;
import com.distributedjobforge.worker_service.domain.JobType;
import com.distributedjobforge.worker_service.executor.ExecutionResult;
import com.distributedjobforge.worker_service.executor.HttpExecutor;
import com.distributedjobforge.worker_service.executor.JavaClassExecutor;
import com.distributedjobforge.worker_service.executor.ShellExecutor;
import com.distributedjobforge.worker_service.registration.WorkerRegistrationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.redisson.api.RLock;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkerKakfaConsumer {

    private final ShellExecutor shellExecutor;
    private final HttpExecutor httpExecutor;
    private final JavaClassExecutor javaClassExecutor;
    private final ResultPublisher resultPublisher;
    private  final WorkerRegistrationService workerRegistrationService ;
    private final RedissonClient redissonClient ;
  private  final MeterRegistry registry;

    @Value("${worker.id:worker-local}")
    private String workerId;

    @KafkaListener(topics = "job.pending", groupId = "djf-workers")
    public void onJobPending(JobPendingMessage message, Acknowledgment ack) {
        UUID jobId = message.jobId();

        log.info("Received job: jobId={}, type={}, attempt={}, priority={}",
                jobId, message.type(), message.attempt(), message.priority());

        // Acquire distributed lock — prevents two workers running the same job
        RLock lock = redissonClient.getFairLock("jobs:lock:" + jobId);
        boolean acquired;
        try {
            acquired = lock.tryLock(0, message.timeoutS() + 60L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted acquiring lock for job {} — not acking", jobId);
            return; // no ack → Kafka redelivers
        }

        if (!acquired) {
            log.warn("Could not acquire lock for job {} — another worker is executing it, skipping", jobId);
            return; // no ack → Kafka redelivers
        }

        workerRegistrationService.markInProgress(jobId);
        try {
            long startTime = System.nanoTime();
            ExecutionResult result = executeJob(message);
            registry.timer("djf.execution.duration").record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            registry.counter("djf.jobs.executed").increment();

            JobResultMessage resultMessage = new JobResultMessage(
                    "1.0",
                    jobId,
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
            ack.acknowledge();
            log.info("Job {} processed and acknowledged", jobId);

        } catch (Exception e) {
            log.error("Unexpected error processing job {}: {}",
                    jobId, e.getMessage(), e);

            JobResultMessage errorResult = new JobResultMessage(
                    "1.0",
                    jobId,
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

        } finally {
            workerRegistrationService.markDone(jobId);
            // Always release lock — even if execution crashed
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock for job {}", jobId);
            }
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
        } else if (message.type() == JobType.JAVA_CLASS) {
            return javaClassExecutor.execute(
                    message.jobId(),
                    message.payload(),
                    message.timeoutS()
            );
        }
        // All known types handled above
        throw new UnsupportedOperationException(
                "Executor not yet implemented for type: " + message.type()
        );
    }
}
