package com.distributedjobforge.scheduler_service.controller;

import com.distributedjobforge.scheduler_service.dto.BatchJobSubmitRequest;
import com.distributedjobforge.scheduler_service.dto.JobResponse;
import com.distributedjobforge.scheduler_service.service.BatchJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Slf4j
public class BatchJobController {

    private final BatchJobService batchJobService;

    @PostMapping("/batch")
    public ResponseEntity<List<JobResponse>> submitBatch(@RequestBody BatchJobSubmitRequest request) {
        return ResponseEntity.ok(batchJobService.submitBatch(request));
    }
}