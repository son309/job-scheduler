package com.example.jobscheduler.repository;

import com.example.jobscheduler.entity.Job;
import com.example.jobscheduler.entity.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Page<Job> findByJobType(String jobType, Pageable pageable);

    Page<Job> findByStatusAndJobType(
            JobStatus status,
            String jobType,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from Job job where job.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") UUID id);

    List<Job> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            JobStatus status,
            Instant now
    );

    List<Job> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            JobStatus status,
            Instant cutoff
    );

    List<Job> findTop100ByStatusOrderByStartedAtAsc(JobStatus status);
}
