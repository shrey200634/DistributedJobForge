package com.distributedjobforge.worker_service.executor;

import com.distributedjobforge.worker_service.domain.JobStatus;
import org.apache.kafka.common.protocol.types.Field;

import java.time.Instant;

public record ExecutionResult (

        JobStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        long durationMs
)
{
    public  static ExecutionResult success (int exitCode , String stdout , String stderr,
                                            Instant startedAt , Instant completedAt ){
        return  new ExecutionResult(
                JobStatus.SUCCEEDED , exitCode , stdout , stderr , null ,
                startedAt, completedAt , completedAt.toEpochMilli()-startedAt.toEpochMilli()
        );
    }

    public  static ExecutionResult failure (int exitCode , String stdout , String stderr
                                            , String errorMessage , Instant startedAt , Instant completedAt ){
        return new ExecutionResult(
                JobStatus.FAILED , exitCode , stdout , stderr , errorMessage
                ,startedAt , completedAt , completedAt.toEpochMilli() - startedAt.toEpochMilli()

        );
    }
    public  static ExecutionResult timeout (String stdout , String stderr ,
                                            Instant startedAt , Instant completedAt){
        return  new ExecutionResult(
                JobStatus.TIMEOUT , null , stdout , stderr , "process exceed timeout ",
                startedAt , completedAt , completedAt.toEpochMilli()-startedAt.toEpochMilli()
        );
    }
}