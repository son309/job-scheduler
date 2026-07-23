package com.example.jobscheduler.controller;

import com.example.jobscheduler.dto.CreateScheduleRequest;
import com.example.jobscheduler.dto.ScheduleResponse;
import com.example.jobscheduler.service.JobScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class JobScheduleController {

    private final JobScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(
            @Valid @RequestBody CreateScheduleRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(scheduleService.create(request));
    }

    @GetMapping
    public List<ScheduleResponse> findAll() {
        return scheduleService.findAll();
    }

    @GetMapping("/{scheduleId}")
    public ScheduleResponse getById(@PathVariable UUID scheduleId) {
        return scheduleService.getById(scheduleId);
    }

    @DeleteMapping("/{scheduleId}")
    public ScheduleResponse disable(@PathVariable UUID scheduleId) {
        return scheduleService.disable(scheduleId);
    }
}
