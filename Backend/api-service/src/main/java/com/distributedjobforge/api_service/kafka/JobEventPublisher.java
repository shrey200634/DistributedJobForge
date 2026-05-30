package com.distributedjobforge.api_service.kafka;

import com.distributedjobforge.api_service.domain.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobEventPublisher {

    public static final String TOPIC_JOB_PENDING = "job.pending";
    public static final String TOPIC_JOB_DLQ = "job.dlq";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishJobPending(Job job) {
        JobPendingMessage message = JobPendingMessage.from(job);
        String key = job.getId().toString();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_JOB_PENDING, key, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish job.pending for jobId={}: {}",
                        job.getId(), ex.getMessage(), ex);
            } else {
                log.info("Published job.pending: jobId={}, partition={}, offset={}",
                        job.getId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void publishToDlq(Job job, int totalAttempts, String lastErrorMessage) {
        JobDlqMessage message = new JobDlqMessage(
                "1.0",
                job.getId(),
                job.getType(),
                job.getIdempotencyKey(),
                totalAttempts,
                lastErrorMessage,
                job.getPayload(),
                Instant.now()
        );
        String key = job.getId().toString();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_JOB_DLQ, key, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish job.dlq for jobId={}: {}",
                        job.getId(), ex.getMessage(), ex);
            } else {
                log.warn("Published job.dlq: jobId={}, totalAttempts={}, partition={}, offset={}",
                        job.getId(), totalAttempts,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
