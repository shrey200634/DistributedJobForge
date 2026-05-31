package com.distributedjobforge.api_service.dto;

import com.distributedjobforge.api_service.domain.JobType;
import jakarta.validation.constraints.*;
import lombok.Builder;

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
        Integer priority,

        @Min(value = 1, message = "Timeout must be between 1 and 3600 seconds")
        @Max(value = 3600, message = "Timeout must be between 1 and 3600 seconds")
        Integer timeoutS,

        @Min(value = 0, message = "Max retries must be between 0 and 10")
        @Max(value = 10, message = "Max retries must be between 0 and 10")
        Integer maxRetries,

        List<UUID> dependsOn,

        List<String> tags,

        @NotNull(message = "Payload is required")
        Map<String, Object> payload
) {
    // Defaults for optional fields.
    // Wrapper types let us tell "not provided" (null) apart from an explicit 0.
    // priority/timeoutS have no valid 0, so null-or-0 -> default.
    // maxRetries=0 is VALID (no retries), so only null -> default.
    public JobSubmitRequest {
        if (priority == null || priority == 0) priority = 5;
        if (timeoutS == null || timeoutS == 0) timeoutS = 300;
        if (maxRetries == null) maxRetries = 5;
        if (dependsOn == null) dependsOn = List.of();
        if (tags == null) tags = List.of();
    }
}
