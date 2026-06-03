package com.distributedjobforge.api_service.dto;

import com.distributedjobforge.api_service.domain.JobType;

import java.util.List;
import java.util.Map;

public record BatchJobItem (
        String clientRefId ,
        String idempotencyKey ,
        JobType type ,
        Integer priority ,
        Integer timeoutS,
        Integer MaxRetries ,
        Map<String , Object > payload ,
        List<String> dependsOn

)
{}