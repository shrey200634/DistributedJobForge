package com.distributedjobforge.worker_service.kafka;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
@Component
@RequiredArgsConstructor
@Slf4j
public class ResultPublisher {
    public static final String TOPIC_JOB_RESULT = "job.result";
    private final KafkaTemplate<String, Object> kafkaTemplate;
    public void publishResult(JobResultMessage result) {
        String key = result.jobId().toString();
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_JOB_RESULT, key, result);
        future.whenComplete((sendResult, ex) -> {
            if (ex != null) {
                log.error("Failed to publish job.result for jobId={}: {}",
                        result.jobId(), ex.getMessage(), ex);
            } else {
                log.info("Published job.result: jobId={}, status={}, partition={}, offset={}",
                        result.jobId(),
                        result.status(),
                        sendResult.getRecordMetadata().partition(),
                        sendResult.getRecordMetadata().offset());
            }
        });
    }
}
