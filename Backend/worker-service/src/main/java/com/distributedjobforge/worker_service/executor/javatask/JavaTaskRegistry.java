package com.distributedjobforge.worker_service.executor.javatask;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JavaTaskRegistry {

    private final Map<String, JavaTask> tasksByName;

    public JavaTaskRegistry(List<JavaTask> tasks) {
        // Build name -> task map. Fail fast if two tasks claim the same name.
        this.tasksByName = tasks.stream()
                .collect(Collectors.toMap(
                        JavaTask::name,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate JavaTask name: '" + a.name() + "' is claimed by "
                                            + a.getClass().getSimpleName() + " and "
                                            + b.getClass().getSimpleName());
                        }
                ));
    }

    @PostConstruct
    public void logRegisteredTasks() {
        log.info("Registered {} JavaTask(s): {}", tasksByName.size(), tasksByName.keySet());
    }
    public Optional<JavaTask> find(String name) {
        return Optional.ofNullable(tasksByName.get(name));
    }
}
