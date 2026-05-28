package com.distributedjobforge.worker_service.executor;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ShellExecutor {

    // execute a shell command with timeout

    @SuppressWarnings("unchecked")
    public  ExecutionResult execute (UUID jobId , Map<String,Object> payload , int timeoutSeconds ){
        Instant startedAt = Instant.now();

        //extract command from payload
        String command = (String) payload.get("command");
        if (command == null || command.isBlank()) {
            return ExecutionResult.failure(
                    -1, "", "Payload missing 'command' field",
                    "Invalid payload: 'command' is required",
                    startedAt, Instant.now()
            );
        }
        Map<String, String> env = (Map<String, String>) payload.getOrDefault("env", Map.of());
        String cwd = (String) payload.get("cwd");

        // Build the process
        ProcessBuilder pb;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("sh", "-c", command);
        }
        pb.environment().putAll(env);
        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new java.io.File(cwd));
        }
        pb.redirectErrorStream(false);

        log.info("Executing job {}: command='{}', timeout={}s", jobId, command, timeoutSeconds);

        Process process;
        try {
            process = pb.start();
        } catch (Exception e) {
            log.error("Failed to start process for job {}: {}", jobId, e.getMessage(), e);
            return ExecutionResult.failure(
                    -1, "", e.getMessage(),
                    "Failed to start process: " + e.getMessage(),
                    startedAt, Instant.now()
            );
        }

        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            if (!finished) {
                process.destroyForcibly();
                log.warn("Job {} exceeded timeout of {}s — process killed", jobId, timeoutSeconds);
                return ExecutionResult.timeout(stdout, stderr, startedAt, Instant.now());
            }

            int exitCode = process.exitValue();
            Instant completedAt = Instant.now();

            if (exitCode == 0) {
                log.info("Job {} completed successfully (exitCode=0)", jobId);
                return ExecutionResult.success(exitCode, stdout, stderr, startedAt, completedAt);
            } else {
                log.warn("Job {} failed (exitCode={})", jobId, exitCode);
                return ExecutionResult.failure(
                        exitCode, stdout, stderr,
                        "Process exited with non-zero code: " + exitCode,
                        startedAt, completedAt
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            log.error("Job {} interrupted: {}", jobId, e.getMessage());
            return ExecutionResult.failure(
                    -1, "", e.getMessage(),
                    "Execution interrupted: " + e.getMessage(),
                    startedAt, Instant.now()
            );
        }
    }

    private String readStream(java.io.InputStream stream) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        } catch (Exception e) {
            log.warn("Failed to read process stream: {}", e.getMessage());
        }
        return sb.toString().trim();
    }
}