package com.example.jobscheduler.controller;

import com.example.jobscheduler.dto.*;
import com.example.jobscheduler.entity.JobStatus;
import com.example.jobscheduler.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> submit(
            @Valid @RequestBody SubmitJobRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(jobService.submit(request));
    }

    @GetMapping("/{jobId}")
    public JobResponse getById(
            @PathVariable UUID jobId
    ) {
        return jobService.getById(jobId);
    }

    @GetMapping
    public PageResponse<JobResponse> findJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String jobType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return jobService.findJobs(
                status,
                jobType,
                page,
                size
        );
    }
}