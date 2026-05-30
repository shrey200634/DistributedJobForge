package com.distributedjobforge.api_service.service;

import com.distributedjobforge.api_service.kafka.JobDlqMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class DlqAlertService {

    @Value("${djf.dlq.webhook-url:}")
    private String webhookUrl;

    @Value("${djf.dlq.webhook-enabled:true}")
    private boolean webhookEnabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendAlert(JobDlqMessage dlqMessage) {
        // Always log the DLQ landing — this is the audit trail
        log.error("DLQ ALERT: jobId={}, type={}, idempotencyKey={}, totalAttempts={}, lastError='{}'",
                dlqMessage.jobId(),
                dlqMessage.type(),
                dlqMessage.idempotencyKey(),
                dlqMessage.totalAttempt(),
                dlqMessage.lastErrorMessage());

        if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("DLQ webhook not configured (djf.dlq.webhook-url is empty) — skipping HTTP alert for jobId={}",
                    dlqMessage.jobId());
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "alert", "JOB_FAILED_DLQ",
                    "jobId", dlqMessage.jobId().toString(),
                    "type", dlqMessage.type().toString(),
                    "idempotencyKey", dlqMessage.idempotencyKey(),
                    "totalAttempts", dlqMessage.totalAttempt(),
                    "lastError", dlqMessage.lastErrorMessage() != null ? dlqMessage.lastErrorMessage() : "",
                    "failedAt", dlqMessage.failedAt().toString()
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("DLQ webhook fired successfully for jobId={} (status={})",
                        dlqMessage.jobId(), response.statusCode());
            } else {
                log.warn("DLQ webhook returned non-2xx for jobId={}: status={}, body={}",
                        dlqMessage.jobId(), response.statusCode(), response.body());
            }

        } catch (Exception e) {
            log.error("Failed to fire DLQ webhook for jobId={}: {}",
                    dlqMessage.jobId(), e.getMessage(), e);
            // Don't rethrow — webhook failure shouldn't crash the consumer
        }
    }
}
