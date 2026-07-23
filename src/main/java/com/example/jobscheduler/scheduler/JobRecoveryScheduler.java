package com.example.jobscheduler.scheduler;

import com.example.jobscheduler.entity.Job;
import com.example.jobscheduler.entity.JobStatus;
import com.example.jobscheduler.queue.JobQueueCoordinator;
import com.example.jobscheduler.repository.JobRepository;
import com.example.jobscheduler.service.JobLifecycleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JobRecoveryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(JobRecoveryScheduler.class);

    private final JobRepository jobRepository;
    private final JobLifecycleService lifecycleService;
    private final JobQueueCoordinator queueCoordinator;

    @Value("${job-queue.queue-stale-after:10s}")
    private Duration queueStaleAfter;

    @Scheduled(fixedDelayString = "${job-queue.recovery-poll-ms:1000}")
    public void recoverJobs() {
        Instant now = Instant.now();
        enqueueDueRetries(now);
        enqueueStalePending(now);
        requeueStaleQueued(now);
        recoverTimedOutWorkers(now);
    }

    private void enqueueDueRetries(Instant now) {
        List<Job> jobs =
                jobRepository
                        .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                                JobStatus.RETRYING,
                                now
                        );

        for (Job job : jobs) {
            runSafely(
                    "enqueue retry",
                    job,
                    () -> queueCoordinator.enqueueRetry(job.getId())
            );
        }
    }

    private void enqueueStalePending(Instant now) {
        List<Job> jobs =
                jobRepository
                        .findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                                JobStatus.PENDING,
                                now.minus(queueStaleAfter)
                        );

        for (Job job : jobs) {
            runSafely(
                    "enqueue pending job",
                    job,
                    () -> queueCoordinator.enqueuePending(job.getId())
            );
        }
    }

    private void requeueStaleQueued(Instant now) {
        List<Job> jobs =
                jobRepository
                        .findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                                JobStatus.QUEUED,
                                now.minus(queueStaleAfter)
                        );

        for (Job job : jobs) {
            runSafely(
                    "requeue stale job",
                    job,
                    () -> queueCoordinator.requeueStale(job.getId())
            );
        }
    }

    private void recoverTimedOutWorkers(Instant now) {
        List<Job> jobs =
                jobRepository.findTop100ByStatusOrderByStartedAtAsc(
                        JobStatus.RUNNING
                );

        for (Job job : jobs) {
            runSafely(
                    "recover timed-out job",
                    job,
                    () -> lifecycleService.recoverTimedOut(job.getId(), now)
            );
        }
    }

    private void runSafely(String action, Job job, Runnable actionBlock) {
        try {
            actionBlock.run();
        } catch (Exception exception) {
            log.error(
                    "Could not {} {}",
                    action,
                    job.getId(),
                    exception
            );
        }
    }
}
