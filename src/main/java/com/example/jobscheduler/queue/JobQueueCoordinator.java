package com.example.jobscheduler.queue;

import com.example.jobscheduler.entity.JobStatus;
import com.example.jobscheduler.service.JobLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JobQueueCoordinator {

    private final JobLifecycleService lifecycleService;
    private final JobQueueProducer queueProducer;

    public boolean enqueuePending(UUID jobId) {
        return transitionAndEnqueue(jobId, JobStatus.PENDING);
    }

    public boolean enqueueRetry(UUID jobId) {
        return transitionAndEnqueue(jobId, JobStatus.RETRYING);
    }

    public boolean requeueStale(UUID jobId) {
        if (!lifecycleService.touchQueued(jobId)) {
            return false;
        }

        queueProducer.enqueue(jobId);
        return true;
    }

    private boolean transitionAndEnqueue(UUID jobId, JobStatus expectedStatus) {
        if (!lifecycleService.moveToQueued(jobId, expectedStatus)) {
            return false;
        }

        queueProducer.enqueue(jobId);
        return true;
    }
}
