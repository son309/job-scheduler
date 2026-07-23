# Distributed Job Scheduler MVP

Spring Boot job queue backed by MySQL and Redis Streams.

## Run

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8080`.

## Built-in job types

- `ECHO`: returns the submitted payload.
- `SLEEP`: sleeps for `payload.durationMs` milliseconds, useful for concurrency
  and timeout testing.
- `FAIL`: always throws, useful for retry testing. An optional
  `payload.message` becomes the stored error.

Add a production job type by implementing
`com.example.jobscheduler.worker.JobHandler` and annotating it with
`@Component`.

## Submit a job

```http
POST /api/jobs
Content-Type: application/json

{
  "jobType": "ECHO",
  "payload": {
    "message": "hello"
  },
  "maxRetries": 3,
  "timeoutSeconds": 60,
  "idempotencyKey": "example-1"
}
```

Read jobs with:

```text
GET /api/jobs/{jobId}
GET /api/jobs?status=SUCCESS&jobType=ECHO&page=0&size=20
```

## Create a periodic job

Cron expressions use Spring's six-field format (including seconds).

```http
POST /api/schedules
Content-Type: application/json

{
  "jobType": "ECHO",
  "payload": {
    "source": "schedule"
  },
  "cronExpression": "0 */5 * * * *",
  "timezone": "Asia/Bangkok",
  "maxRetries": 3,
  "timeoutSeconds": 60
}
```

Manage schedules with:

```text
GET /api/schedules
GET /api/schedules/{scheduleId}
DELETE /api/schedules/{scheduleId}
```

`DELETE` disables the schedule and keeps its history.

## Execution behavior

- Redis consumer groups distribute work across the configured worker pool.
- MySQL row locks prevent duplicate execution when Redis redelivers a message.
- Failed jobs retry with exponential backoff: 5, 15, 45 seconds, capped at
  5 minutes.
- Execution is cancelled when `timeoutSeconds` is exceeded.
- Recovery tasks re-enqueue stale `PENDING`/`QUEUED` jobs, recover timed-out
  `RUNNING` jobs, and claim abandoned Redis pending messages.
- Periodic occurrences use deterministic idempotency keys to avoid duplicate
  jobs when multiple application instances run.

Worker and recovery settings are under `job-queue` in
`src/main/resources/application.yaml`.
