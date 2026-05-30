package com.distributedjobforge.api_service.service;

import com.distributedjobforge.api_service.domain.Job;
import com.distributedjobforge.api_service.domain.JobExecution;
import com.distributedjobforge.api_service.domain.JobStatus;
import com.distributedjobforge.api_service.exception.JobNotFoundException;
import com.distributedjobforge.api_service.kafka.JobResultMessage;
import com.distributedjobforge.api_service.repository.JobExecutionRepo;
import com.distributedjobforge.api_service.repository.JobRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobResultProcessor {

    private final JobRepo jobRepo ;
    private final JobExecutionRepo jobExecutionRepo ;

    @Transactional
    private void processResult (JobResultMessage resultMessage){
        // find the job
        Job job = jobRepo.findById(resultMessage.jobId())
                .orElseThrow(()-> new JobNotFoundException(resultMessage.jobId()));

        log.info("Processing result for jobId={}, status={}, workerId={}",
                resultMessage.jobId(), resultMessage.status(), resultMessage.workerID());

        //update the entity with execution outcome

        job.setStatus(resultMessage.status());
        job.setWorkerId(resultMessage.workerID());
        job.setStartedAt(resultMessage.startedAt());
        job.setCompletedAt(resultMessage.completedAt());
        job.setRetryCount(resultMessage.attempt());

        if (resultMessage.status() == JobStatus.FAILED || resultMessage.status()==JobStatus.TIMEOUT){
            job.setErrorMessage(resultMessage.errorMessage());
        }

        // Store stdout/stderr in result JSON (for SUCCEEDED jobs)
        if (resultMessage.stdout() != null || resultMessage.stderr() != null) {
            job.setResult(java.util.Map.of(
                    "stdout", resultMessage.stdout() != null ? resultMessage.stdout() : "",
                    "stderr", resultMessage.stderr() != null ? resultMessage.stderr() : "",
                    "exitCode", resultMessage.exitCode() != null ? resultMessage.exitCode() : -1,
                    "durationMs", resultMessage.durationMs() != null ? resultMessage.durationMs() : 0L
            ));
        }

        jobRepo.save(job);

        //create a JobExecution  audit record
        JobExecution execution =JobExecution.builder()
                .jobId(resultMessage.jobId())
                .attempt(resultMessage.attempt())
                .workerId(resultMessage.workerID())
                .startedAt(resultMessage.startedAt())
                .endAt(resultMessage.completedAt())
                .exitCode(resultMessage.exitCode())
                .stdout(resultMessage.stdout())
                .stderr(resultMessage.stderr())
                .durationMs(resultMessage.durationMs())
                .status(resultMessage.status())
                .build();

        jobExecutionRepo.save(execution);

        log.info("Job {} updated: status={}, attempt={}, durationMs={}",
                resultMessage.jobId(), resultMessage.status(), resultMessage.attempt(), resultMessage.durationMs());



    }
}
