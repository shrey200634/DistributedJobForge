package com.distributedjobforge.scheduler_service.kafka;

import com.distributedjobforge.scheduler_service.service.DagProgressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobCompletedConsumer {

    private final DagProgressionService dagProgressionService;

    @KafkaListener(
            topics = "job.completed",
            groupId = "djf-scheduler",
            properties = {
                    "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            }
    )
    public void onJobCompleted(ConsumerRecord<String, String> record) {
        UUID jobId = UUID.fromString(record.value());
        log.info("Received job.completed event for jobId={}", jobId);
        dagProgressionService.onJobSucceeded(jobId);
    }
}