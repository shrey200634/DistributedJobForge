package com.distributedjobforge.api_service.dto;

import java.util.List;

public record BatchJobSubmitRequest(
        List<BatchJobItem> jobs
) {
}
