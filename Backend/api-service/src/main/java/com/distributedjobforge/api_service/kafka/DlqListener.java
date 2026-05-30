package com.distributedjobforge.api_service.kafka;

import com.distributedjobforge.api_service.service.DlqAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DlqListener {

    private final DlqAlertService dlqAlertService;

    @KafkaListener(
            topics = "job.dlq",
            groupId = "djf-dlq-alerts",
            properties = {
                    "spring.json.value.default.type=com.distributedjobforge.api_service.kafka.JobDlqMessage",
                    "spring.json.use.type.headers=false"
            }
    )
    public void onDlqMessage(JobDlqMessage message) {
        log.warn("Received DLQ message: jobId={}, totalAttempts={}",
                message.jobId(), message.totalAttempt());
        dlqAlertService.sendAlert(message);
    }
}
