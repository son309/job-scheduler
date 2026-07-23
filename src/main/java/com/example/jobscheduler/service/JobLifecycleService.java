package com.example.jobscheduler.service;

import com.example.jobscheduler.entity.Job;
import com.example.jobscheduler.entity.JobStatus;
import com.example.jobscheduler.exception.JobNotFoundException;
import com.example.jobscheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobLifecycleService {

    private static final long INITIAL_RETRY_DELAY_SECONDS = 5;
    private static final long MAX_RETRY_DELAY_SECONDS = 300;

    private final JobRepository jobRepository;

    @Transactional
    public Job create(Job job) {
        return jobRepository.save(job);
    }

    @Transactional
    public boolean moveToQueued(UUID jobId, JobStatus expectedStatus) {
        Job job = findForUpdate(jobId);

        if (job.getStatus() != expectedStatus) {
            return false;
        }

        job.setStatus(JobStatus.QUEUED);
        job.setNextAttemptAt(null);
        job.setWorkerId(null);
        return true;
    }

    @Transactional
    public boolean touchQueued(UUID jobId) {
        Job job = findForUpdate(jobId);

        if (job.getStatus() != JobStatus.QUEUED) {
            return false;
        }

        job.setUpdatedAt(Instant.now());
        return true;
    }

    @Transactional
    public Optional<ClaimedJob> claim(UUID jobId, String workerId) {
        Job job = findForUpdate(jobId);

        if (job.getStatus() != JobStatus.QUEUED) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        job.setStatus(JobStatus.RUNNING);
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setWorkerId(workerId);
        job.setStartedAt(now);
        job.setFinishedAt(null);
        job.setErrorMessage(null);
        job.setNextAttemptAt(null);

        return Optional.of(new ClaimedJob(
                job.getId(),
                job.getJobType(),
                job.getPayload(),
                job.getAttemptCount(),
                job.getMaxRetries(),
                job.getTimeoutSeconds(),
                workerId
        ));
    }

    @Transactional
    public void completeSuccess(UUID jobId, String workerId, String result) {
        Job job = findForUpdate(jobId);

        if (!isOwnedRunningJob(job, workerId)) {
            return;
        }

        job.setStatus(JobStatus.SUCCESS);
        job.setResult(result);
        job.setErrorMessage(null);
        job.setFinishedAt(Instant.now());
        job.setWorkerId(null);
        job.setNextAttemptAt(null);
    }

    @Transactional
    public void completeFailure(
            UUID jobId,
            String workerId,
            String errorMessage,
            boolean timedOut
    ) {
        Job job = findForUpdate(jobId);

        if (!isOwnedRunningJob(job, workerId)) {
            return;
        }

        transitionAfterFailure(job, errorMessage, timedOut, Instant.now());
    }

    @Transactional
    public boolean recoverTimedOut(UUID jobId, Instant now) {
        Job job = findForUpdate(jobId);

        if (job.getStatus() != JobStatus.RUNNING || job.getStartedAt() == null) {
            return false;
        }

        Instant deadline = job.getStartedAt().plusSeconds(job.getTimeoutSeconds());
        if (deadline.isAfter(now)) {
            return false;
        }

        transitionAfterFailure(
                job,
                "Worker stopped responding before the timeout deadline",
                true,
                now
        );
        return true;
    }

    private void transitionAfterFailure(
            Job job,
            String errorMessage,
            boolean timedOut,
            Instant now
    ) {
        job.setErrorMessage(limitErrorMessage(errorMessage));
        job.setWorkerId(null);

        if (job.getAttemptCount() <= job.getMaxRetries()) {
            job.setStatus(JobStatus.RETRYING);
            job.setNextAttemptAt(now.plus(retryDelay(job.getAttemptCount())));
            job.setFinishedAt(null);
            return;
        }

        job.setStatus(timedOut ? JobStatus.TIMED_OUT : JobStatus.FAILED);
        job.setNextAttemptAt(null);
        job.setFinishedAt(now);
    }

    private Duration retryDelay(int attemptCount) {
        long multiplier = 1;
        for (int index = 1; index < attemptCount; index++) {
            multiplier = Math.min(multiplier * 3, MAX_RETRY_DELAY_SECONDS);
        }

        long delay = Math.min(
                INITIAL_RETRY_DELAY_SECONDS * multiplier,
                MAX_RETRY_DELAY_SECONDS
        );
        return Duration.ofSeconds(delay);
    }

    private boolean isOwnedRunningJob(Job job, String workerId) {
        return job.getStatus() == JobStatus.RUNNING
                && workerId.equals(job.getWorkerId());
    }

    private String limitErrorMessage(String message) {
        String safeMessage = message == null ? "Job execution failed" : message;
        return safeMessage.length() <= 4000
                ? safeMessage
                : safeMessage.substring(0, 4000);
    }

    private Job findForUpdate(UUID jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    public record ClaimedJob(
            UUID id,
            String jobType,
            String payload,
            int attemptCount,
            int maxRetries,
            int timeoutSeconds,
            String workerId
    ) {
    }
}
