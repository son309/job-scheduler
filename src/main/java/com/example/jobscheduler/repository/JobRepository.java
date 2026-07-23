package com.example.jobscheduler.repository;

import com.example.jobscheduler.entity.Job;
import com.example.jobscheduler.entity.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}