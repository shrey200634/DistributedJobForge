package com.distributedjobforge.api_service.controller;


import com.distributedjobforge.api_service.dto.JobResponse;
import com.distributedjobforge.api_service.dto.JobSubmitRequest;
import com.distributedjobforge.api_service.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final JobService jobService;


    @PostMapping
    public ResponseEntity<JobResponse> submitJob(@Valid @RequestBody JobSubmitRequest request){
        log.info("Received job submission: idempotencyKey={}, type={}",
                request.idempotencyKey() , request.type());
        JobResponse response = jobService.submitJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{jobId}")
    public  ResponseEntity<JobResponse>  getJob (@PathVariable UUID jobId ){
        JobResponse response = jobService.getJob(jobId);
        return ResponseEntity.ok(response);

    }

    @DeleteMapping("/{jobId}")
    public  ResponseEntity<JobResponse> cancelJob ( @PathVariable UUID jobId  ){
        JobResponse response = jobService.cancelJob(jobId);
        return ResponseEntity.ok(response);

    }
}
