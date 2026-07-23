package com.example.jobscheduler.entity;

public enum JobStatus {
    PENDING,
    QUEUED,
    RUNNING,
    RETRYING,
    SUCCESS,
    FAILED,
    TIMED_OUT,
    DEAD
}