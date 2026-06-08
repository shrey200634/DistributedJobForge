package com.distributedjobforge.scheduler_service.exception;

public class CycleDepededncyException extends  RuntimeException{

    public  CycleDepededncyException(String message ){
        super(message);
    }

}
