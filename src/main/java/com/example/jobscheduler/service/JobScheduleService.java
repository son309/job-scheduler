package com.example.jobscheduler.service;

import com.example.jobscheduler.dto.CreateScheduleRequest;
import com.example.jobscheduler.dto.ScheduleResponse;
import com.example.jobscheduler.entity.Job;
import com.example.jobscheduler.entity.JobSchedule;
import com.example.jobscheduler.entity.JobStatus;
import com.example.jobscheduler.repository.JobRepository;
import com.example.jobscheduler.repository.JobScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobScheduleService {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final JobScheduleRepository scheduleRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        ZoneId zoneId = parseZone(request.timezone());
        CronExpression cron = parseCron(request.cronExpression());
        Instant nextRunAt = nextRun(cron, zoneId, Instant.now());

        JobSchedule schedule = JobSchedule.builder()
                .id(UUID.randomUUID())
                .jobType(normalizeJobType(request.jobType()))
                .payload(writeJson(request.payload()))
                .cronExpression(request.cronExpression().trim())
                .timezone(zoneId.getId())
                .maxRetries(request.maxRetries() == null
                        ? DEFAULT_MAX_RETRIES
                        : request.maxRetries())
                .timeoutSeconds(request.timeoutSeconds() == null
                        ? DEFAULT_TIMEOUT_SECONDS
                        : request.timeoutSeconds())
                .enabled(true)
                .nextRunAt(nextRunAt)
                .build();

        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAll() {
        return scheduleRepository.findAll(
                        Sort.by(Sort.Direction.ASC, "nextRunAt")
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getById(UUID id) {
        return toResponse(scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Schedule not found: " + id
                )));
    }

    @Transactional
    public ScheduleResponse disable(UUID id) {
        JobSchedule schedule = scheduleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Schedule not found: " + id
                ));
        schedule.setEnabled(false);
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public List<JobSchedule> findDue(Instant now) {
        return scheduleRepository
                .findTop100ByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                        now
                );
    }

    @Transactional
    public Optional<UUID> materializeOccurrence(
            UUID scheduleId,
            Instant expectedRunAt
    ) {
        JobSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElse(null);

        if (schedule == null
                || !schedule.isEnabled()
                || !schedule.getNextRunAt().equals(expectedRunAt)) {
            return Optional.empty();
        }

        String idempotencyKey =
                "schedule:" + schedule.getId() + ":" + expectedRunAt.toEpochMilli();

        Optional<Job> existing =
                jobRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            advanceSchedule(schedule, expectedRunAt);
            return Optional.of(existing.get().getId());
        }

        Job job = Job.builder()
                .id(UUID.randomUUID())
                .jobType(schedule.getJobType())
                .payload(schedule.getPayload())
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxRetries(schedule.getMaxRetries())
                .timeoutSeconds(schedule.getTimeoutSeconds())
                .idempotencyKey(idempotencyKey)
                .build();

        jobRepository.save(job);
        advanceSchedule(schedule, expectedRunAt);
        return Optional.of(job.getId());
    }

    private void advanceSchedule(JobSchedule schedule, Instant currentRunAt) {
        CronExpression cron = parseCron(schedule.getCronExpression());
        ZoneId zoneId = parseZone(schedule.getTimezone());
        schedule.setLastRunAt(currentRunAt);
        schedule.setNextRunAt(nextRun(cron, zoneId, currentRunAt));
    }

    private Instant nextRun(
            CronExpression cron,
            ZoneId zoneId,
            Instant after
    ) {
        ZonedDateTime next = cron.next(ZonedDateTime.ofInstant(after, zoneId));
        if (next == null) {
            throw new IllegalArgumentException(
                    "Cron expression has no future execution time"
            );
        }
        return next.toInstant();
    }

    private CronExpression parseCron(String expression) {
        try {
            return CronExpression.parse(expression.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid cron expression: " + expression,
                    exception
            );
        }
    }

    private ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid timezone: " + timezone,
                    exception
            );
        }
    }

    private String normalizeJobType(String jobType) {
        return jobType.trim().toUpperCase();
    }

    private String writeJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid schedule payload", exception);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not read schedule payload",
                    exception
            );
        }
    }

    private ScheduleResponse toResponse(JobSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getJobType(),
                readJson(schedule.getPayload()),
                schedule.getCronExpression(),
                schedule.getTimezone(),
                schedule.getMaxRetries(),
                schedule.getTimeoutSeconds(),
                schedule.isEnabled(),
                schedule.getNextRunAt(),
                schedule.getLastRunAt(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }
}
