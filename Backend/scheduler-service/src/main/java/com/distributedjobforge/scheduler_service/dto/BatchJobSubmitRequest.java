package com.distributedjobforge.scheduler_service.dto;

import java.util.List;

public record BatchJobSubmitRequest(
        List<BatchJobItem> jobs
) {
}
