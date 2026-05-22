package com.distributedjobforge.api_service.exception;

import java.util.UUID;

public class JobNotFoundException extends  RuntimeException {
    public  JobNotFoundException(UUID jobId ){
        super("Job Not Found: " + jobId);
    }

    public  JobNotFoundException(String message ){
        super(message);
    }

}
