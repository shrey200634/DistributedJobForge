package com.distributedjobforge.api_service.domain;

public  enum JobStatus{

    PENDING ,
    QUEUED,
    RUNNING ,
    SUCCEEDED,
    FAILED ,
    RETRYING ,
    DLQ ,
    CANCELLED ,
    BLOCKED
}