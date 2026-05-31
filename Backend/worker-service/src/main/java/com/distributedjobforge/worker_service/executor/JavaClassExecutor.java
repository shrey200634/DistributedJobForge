package com.distributedjobforge.worker_service.executor;

import com.distributedjobforge.worker_service.executor.javatask.JavaTask;
import com.distributedjobforge.worker_service.executor.javatask.JavaTaskRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a pre-registered JavaTask, selected by name from the payload.
 * The payload never carries code — only a task name + args — so this is
 * safe by construction (no arbitrary class loading / RCE).
 *
 * Mirrors HttpExecutor: the (potentially slow) task runs on a virtual
 * thread, guarded by the job's timeout.
 */
@Component
@Slf4j
public class JavaClassExecutor {

    private final ExecutorService virtualThreadExecutor;
    private final JavaTaskRegistry registry;

    public JavaClassExecutor(@Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor,
                             JavaTaskRegistry registry) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public ExecutionResult execute(UUID jobId, Map<String, Object> payload, int timeoutSeconds) {
        Instant startedAt = Instant.now();

        // 1. Which task? The payload selects it by name.
        String taskName = (String) payload.get("taskName");
        if (taskName == null || taskName.isBlank()) {
            return ExecutionResult.failure(
                    -1, "", "Payload missing 'taskName' field",
                    "Invalid payload: 'taskName' is required",
                    startedAt, Instant.now()
            );
        }

        // 2. SECURITY GATE: only pre-registered tasks can run.
        Optional<JavaTask> maybeTask = registry.find(taskName);
        if (maybeTask.isEmpty()) {
            log.warn("Java job {} requested unknown task '{}'", jobId, taskName);
            return ExecutionResult.failure(
                    -1, "", "Unknown task: " + taskName,
                    "No JavaTask registered under name '" + taskName + "'",
                    startedAt, Instant.now()
            );
        }
        JavaTask task = maybeTask.get();

        // 3. Args for the task (may be absent -> empty map).
        Map<String, Object> args =
                (Map<String, Object>) payload.getOrDefault("args", Map.of());

        log.info("Executing JAVA_CLASS job {}: task='{}' (timeout={}s)",
                jobId, taskName, timeoutSeconds);

        // 4. Run the task on a virtual thread.
        Future<String> future = virtualThreadExecutor.submit(() -> task.run(args));

        try {
            // 5. Wait, guarded by the job timeout (+5s slack).
            String output = future.get(timeoutSeconds + 5L, TimeUnit.SECONDS);
            Instant completedAt = Instant.now();
            log.info("JAVA_CLASS job {} succeeded (task='{}')", jobId, taskName);
            // exitCode 0 = success, output becomes stdout
            return ExecutionResult.success(0, output != null ? output : "", "", startedAt, completedAt);

        } catch (TimeoutException e) {
            future.cancel(true); // interrupts the task thread (e.g. wakes Thread.sleep)
            log.warn("JAVA_CLASS job {} timed out after {}s", jobId, timeoutSeconds);
            return ExecutionResult.timeout("", "Task exceeded timeout", startedAt, Instant.now());

        } catch (Exception e) {
            future.cancel(true);
            // ExecutionException wraps whatever the task threw; unwrap for a clean message.
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;

            // If the task was interrupted by our cancel (e.g. SleepTask), treat as TIMEOUT.
            if (cause instanceof InterruptedException) {
                log.warn("JAVA_CLASS job {} interrupted (timeout) for task '{}'", jobId, taskName);
                return ExecutionResult.timeout("", "Task interrupted (timeout)", startedAt, Instant.now());
            }

            log.error("JAVA_CLASS job {} failed (task='{}'): {}", jobId, taskName, cause.getMessage());
            return ExecutionResult.failure(
                    -1, "", cause.getMessage(),
                    "Task failed: " + cause.getMessage(),
                    startedAt, Instant.now()
            );
        }
    }
}
