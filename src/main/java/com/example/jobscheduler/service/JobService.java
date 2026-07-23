package com.example.jobscheduler.service;

import com.example.jobscheduler.dto.JobResponse;
import com.example.jobscheduler.dto.PageResponse;
import com.example.jobscheduler.dto.SubmitJobRequest;
import com.example.jobscheduler.entity.Job;
import com.example.jobscheduler.entity.JobStatus;
import com.example.jobscheduler.exception.JobNotFoundException;
import com.example.jobscheduler.queue.JobQueueCoordinator;
import com.example.jobscheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_PAGE_SIZE = 100;

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final JobLifecycleService lifecycleService;
    private final JobQueueCoordinator queueCoordinator;

    public JobResponse submit(SubmitJobRequest request) {
        String idempotencyKey =
                normalizeIdempotencyKey(request.idempotencyKey());

        /*
         * Nếu client gửi lại cùng idempotencyKey,
         * trả về job đã có thay vì tạo job mới.
         */
        if (idempotencyKey != null) {
            var existingJob =
                    jobRepository.findByIdempotencyKey(idempotencyKey);

            if (existingJob.isPresent()) {
                Job job = existingJob.get();
                if (job.getStatus() == JobStatus.PENDING) {
                    queueCoordinator.enqueuePending(job.getId());
                    return getById(job.getId());
                }
                return toResponse(job);
            }
        }

        Job job = Job.builder()
                .id(UUID.randomUUID())
                .jobType(normalizeJobType(request.jobType()))
                .payload(writeJson(request.payload()))
                .status(JobStatus.PENDING)
                .attemptCount(0)
                .maxRetries(
                        request.maxRetries() == null
                                ? DEFAULT_MAX_RETRIES
                                : request.maxRetries()
                )
                .timeoutSeconds(
                        request.timeoutSeconds() == null
                                ? DEFAULT_TIMEOUT_SECONDS
                                : request.timeoutSeconds()
                )
                .idempotencyKey(idempotencyKey)
                .build();

        /*
         * saveAndFlush giúp lệnh INSERT được gửi xuống MySQL
         * trước khi đẩy jobId sang Redis.
         */
        Job savedJob = lifecycleService.create(job);
        queueCoordinator.enqueuePending(savedJob.getId());
        return getById(savedJob.getId());
    }

    @Transactional(readOnly = true)
    public JobResponse getById(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> findJobs(
            JobStatus status,
            String jobType,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
        );

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                )
        );

        boolean hasJobType =
                jobType != null && !jobType.isBlank();

        Page<Job> jobs;

        if (status != null && hasJobType) {
            jobs = jobRepository.findByStatusAndJobType(
                    status,
                    normalizeJobType(jobType),
                    pageable
            );
        } else if (status != null) {
            jobs = jobRepository.findByStatus(
                    status,
                    pageable
            );
        } else if (hasJobType) {
            jobs = jobRepository.findByJobType(
                    normalizeJobType(jobType),
                    pageable
            );
        } else {
            jobs = jobRepository.findAll(pageable);
        }

        Page<JobResponse> responsePage =
                jobs.map(this::toResponse);

        return PageResponse.from(responsePage);
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getJobType(),
                readJson(job.getPayload()),
                job.getStatus(),
                job.getAttemptCount(),
                job.getMaxRetries(),
                job.getTimeoutSeconds(),
                job.getIdempotencyKey(),
                readJson(job.getResult()),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getNextAttemptAt(),
                job.getWorkerId(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private String normalizeJobType(String jobType) {
        return jobType.trim().toUpperCase();
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        return key.trim();
    }

    private String writeJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Invalid JSON payload",
                    exception
            );
        }
    }

    private JsonNode readJson(String json) {
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not read JSON stored in database",
                    exception
            );
        }
    }
}
