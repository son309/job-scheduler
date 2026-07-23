package com.example.jobscheduler.scheduler;

import com.example.jobscheduler.entity.JobSchedule;
import com.example.jobscheduler.queue.JobQueueCoordinator;
import com.example.jobscheduler.service.JobScheduleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PeriodicJobScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(PeriodicJobScheduler.class);

    private final JobScheduleService scheduleService;
    private final JobQueueCoordinator queueCoordinator;

    @Scheduled(fixedDelayString = "${job-queue.schedule-poll-ms:1000}")
    public void createDueJobs() {
        List<JobSchedule> schedules = scheduleService.findDue(Instant.now());

        for (JobSchedule schedule : schedules) {
            try {
                scheduleService.materializeOccurrence(
                                schedule.getId(),
                                schedule.getNextRunAt()
                        )
                        .ifPresent(queueCoordinator::enqueuePending);
            } catch (Exception exception) {
                log.error(
                        "Could not materialize schedule {}",
                        schedule.getId(),
                        exception
                );
            }
        }
    }
}
