package com.distributedjobforge.worker_service.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class HttpExecutor {

    private final ExecutorService virtualThreadExecutor;
    private final HttpClient httpClient;

    public HttpExecutor(@Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @SuppressWarnings("unchecked")
    public ExecutionResult execute(UUID jobId, Map<String, Object> payload, int timeoutSeconds) {
        Instant startedAt = Instant.now();
        String url = (String) payload.get("url");
        if (url == null || url.isBlank()) {
            return ExecutionResult.failure(
                    -1, "", "Payload missing 'url' field",
                    "Invalid payload: 'url' is required",
                    startedAt, Instant.now()
            );
        }

        String method = ((String) payload.getOrDefault("method", "GET")).toUpperCase();
        String body = (String) payload.getOrDefault("body", "");
        Map<String, Object> headers = (Map<String, Object>) payload.getOrDefault("headers", Map.of());
        int expectedStatus = ((Number) payload.getOrDefault("expectedStatus", 200)).intValue();

        // 2. Build the HttpRequest.
        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .method(method, bodyPublisherFor(method, body));

            headers.forEach((k, v) -> builder.header(k, String.valueOf(v)));
            request = builder.build();
        } catch (Exception e) {
            log.error("Failed to build HTTP request for job {}: {}", jobId, e.getMessage());
            return ExecutionResult.failure(
                    -1, "", e.getMessage(),
                    "Invalid HTTP request: " + e.getMessage(),
                    startedAt, Instant.now()
            );
        }

        log.info("Executing HTTP job {}: {} {} (timeout={}s, expect={})",
                jobId, method, url, timeoutSeconds, expectedStatus);
        Future<HttpResponse<String>> future = virtualThreadExecutor.submit(
                () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        );

        try {

            HttpResponse<String> response =
                    future.get(timeoutSeconds + 5L, TimeUnit.SECONDS);

            int statusCode = response.statusCode();
            String responseBody = response.body();
            Instant completedAt = Instant.now();

            if (statusCode == expectedStatus) {
                log.info("HTTP job {} succeeded (status={})", jobId, statusCode);
                return ExecutionResult.success(statusCode, responseBody, "", startedAt, completedAt);
            } else {
                log.warn("HTTP job {} got status={}, expected={}", jobId, statusCode, expectedStatus);
                return ExecutionResult.failure(
                        statusCode, responseBody,
                        "Unexpected status code",
                        "Expected HTTP " + expectedStatus + " but got " + statusCode,
                        startedAt, completedAt
                );
            }

        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("HTTP job {} timed out after {}s", jobId, timeoutSeconds);
            return ExecutionResult.timeout("", "Request exceeded timeout", startedAt, Instant.now());

        } catch (Exception e) {
            future.cancel(true);
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            log.error("HTTP job {} failed: {}", jobId, cause.getMessage());
            return ExecutionResult.failure(
                    -1, "", cause.getMessage(),
                    "HTTP request failed: " + cause.getMessage(),
                    startedAt, Instant.now()
            );
        }
    }
    private HttpRequest.BodyPublisher bodyPublisherFor(String method, String body) {
        if (body == null || body.isBlank()
                || method.equals("GET") || method.equals("DELETE")) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(body);
    }
}
