package com.distributedjobforge.api_service.dto;

import com.distributedjobforge.api_service.domain.JobType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record JobSubmitRequest(

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey,

        @NotNull(message = "Job type is required")
        JobType type,

        @Min(value = 1, message = "Priority must be between 1 and 10")
        @Max(value = 10, message = "Priority must be between 1 and 10")
        int priority,

        @Min(value = 1, message = "Timeout must be between 1 and 3600 seconds")
        @Max(value = 3600, message = "Timeout must be between 1 and 3600 seconds")
        int timeoutS,

        @Min(value = 0, message = "Max retries must be between 0 and 10")
        @Max(value = 10, message = "Max retries must be between 0 and 10")
        int maxRetries,

        List<UUID> dependsOn,

        List<String> tags,

        @NotNull(message = "Payload is required")
        Map<String, Object> payload
) {
    // Defaults for optional fields
    public JobSubmitRequest {
        if (priority == 0) priority = 5;
        if (timeoutS == 0) timeoutS = 300;
        if (maxRetries == 0) maxRetries = 5;
        if (dependsOn == null) dependsOn = List.of();
        if (tags == null) tags = List.of();
    }
}
