package com.distributedjobforge.api_service.exception;

public class InvalidJobStateException extends  RuntimeException {
    public  InvalidJobStateException(String message ){
        super(message);
    }
}
