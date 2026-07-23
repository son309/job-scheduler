package com.example.jobscheduler.repository;

import com.example.jobscheduler.entity.JobSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobScheduleRepository extends JpaRepository<JobSchedule, UUID> {

    List<JobSchedule> findTop100ByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select schedule from JobSchedule schedule where schedule.id = :id")
    Optional<JobSchedule> findByIdForUpdate(@Param("id") UUID id);
}
