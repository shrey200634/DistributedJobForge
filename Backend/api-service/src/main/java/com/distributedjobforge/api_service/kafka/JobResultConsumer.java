package com.distributedjobforge.api_service.kafka;

import com.distributedjobforge.api_service.service.JobResultProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobResultConsumer {

    private final JobResultProcessor jobResultProcessor;

    @KafkaListener(
            topics = "job.result",
            groupId = "djf-results",
            properties = {
                    "spring.json.value.default.type=com.distributedjobforge.api_service.kafka.JobResultMessage",
                    "spring.json.use.type.headers=false"
            }
    )
    public void onJobResult(JobResultMessage message) {
        log.info("Received result: jobId={}, status={}, workerId={}",
                message.jobId(), message.status(), message.workerId());

        try {
            jobResultProcessor.processResult(message);
        } catch (Exception e) {
            log.error("Failed to process result for jobId={}: {}",
                    message.jobId(), e.getMessage(), e);
            // Don't rethrow — we don't want to block the entire result topic on one bad message
            // Phase 2 will add proper DLQ handling for this
        }
    }
}
