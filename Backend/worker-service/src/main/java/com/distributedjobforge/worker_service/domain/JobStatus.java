package com.distributedjobforge.worker_service.domain;

public enum JobStatus {
    PENDING ,
    QUEUED ,
    RUNNING ,
    SUCCEEDED,
    FAILED ,
    RETRYING ,
    DLQ ,
    CANCELLED ,
    BLOCKED ,
    TIMEOUT



}
