package com.example.jobscheduler.worker;

import com.example.jobscheduler.queue.QueueConstants;
import com.example.jobscheduler.service.JobLifecycleService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class RedisJobWorker {

    private static final Logger log =
            LoggerFactory.getLogger(RedisJobWorker.class);

    private final StringRedisTemplate redisTemplate;
    private final JobLifecycleService lifecycleService;
    private final JobHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;

    @Value("${job-queue.stream-key}")
    private String streamKey;

    @Value("${job-queue.consumer-group}")
    private String consumerGroup;

    @Value("${job-queue.worker-count:4}")
    private int workerCount;

    @Value("${job-queue.poll-timeout:2s}")
    private Duration pollTimeout;

    @Value("${job-queue.pending-idle:15s}")
    private Duration pendingIdle;

    private volatile boolean running;
    private String recoveryConsumerName;
    private ExecutorService consumerExecutor;
    private ExecutorService handlerExecutor;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running = true;
        consumerExecutor = Executors.newFixedThreadPool(
                workerCount,
                namedThreadFactory("job-consumer-")
        );
        handlerExecutor = Executors.newFixedThreadPool(
                workerCount,
                namedThreadFactory("job-handler-")
        );

        String instanceId = UUID.randomUUID().toString().substring(0, 8);
        recoveryConsumerName = instanceId + "-recovery";
        for (int index = 0; index < workerCount; index++) {
            String consumerName = instanceId + "-" + index;
            consumerExecutor.submit(() -> consumeLoop(consumerName));
        }
    }

    @Scheduled(fixedDelayString = "${job-queue.pending-recovery-poll-ms:5000}")
    public void reclaimPendingMessages() {
        if (!running || recoveryConsumerName == null) {
            return;
        }

        try {
            PendingMessages pendingMessages =
                    redisTemplate.opsForStream().pending(
                            streamKey,
                            consumerGroup,
                            Range.unbounded(),
                            100,
                            pendingIdle
                    );

            if (pendingMessages.isEmpty()) {
                return;
            }

            RecordId[] ids = pendingMessages.stream()
                    .map(PendingMessage::getId)
                    .toArray(RecordId[]::new);

            List<MapRecord<String, Object, Object>> claimedRecords =
                    redisTemplate.opsForStream().claim(
                            streamKey,
                            consumerGroup,
                            recoveryConsumerName,
                            pendingIdle,
                            ids
                    );

            for (MapRecord<String, Object, Object> record : claimedRecords) {
                if (processRecord(record, recoveryConsumerName)) {
                    acknowledge(record.getId());
                }
            }
        } catch (Exception exception) {
            log.error("Could not reclaim pending Redis messages", exception);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
        }
        if (handlerExecutor != null) {
            handlerExecutor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private void consumeLoop(String consumerName) {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records =
                        redisTemplate.opsForStream().read(
                                Consumer.from(consumerGroup, consumerName),
                                StreamReadOptions.empty()
                                        .count(1)
                                        .block(pollTimeout),
                                StreamOffset.create(
                                        streamKey,
                                        ReadOffset.lastConsumed()
                                )
                        );

                if (records == null) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    if (processRecord(record, consumerName)) {
                        acknowledge(record.getId());
                    }
                }
            } catch (Exception exception) {
                if (running) {
                    log.error(
                            "Worker {} could not read from Redis Stream",
                            consumerName,
                            exception
                    );
                    pauseAfterReadFailure();
                }
            }
        }
    }

    private boolean processRecord(
            MapRecord<String, Object, Object> record,
            String consumerName
    ) {
        Object rawJobId = record.getValue().get(QueueConstants.JOB_ID_FIELD);
        if (rawJobId == null) {
            return true;
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(rawJobId.toString());
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring Redis message with invalid jobId: {}", rawJobId);
            return true;
        }

        Optional<JobLifecycleService.ClaimedJob> claim =
                lifecycleService.claim(jobId, consumerName);
        if (claim.isEmpty()) {
            return true;
        }

        return executeClaimedJob(claim.get());
    }

    private boolean executeClaimedJob(JobLifecycleService.ClaimedJob job) {
        Future<JsonNode> future = handlerExecutor.submit(() -> {
            JobHandler handler = handlerRegistry.find(job.jobType())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported jobType: " + job.jobType()
                    ));
            JsonNode payload = objectMapper.readTree(job.payload());
            return handler.execute(payload);
        });

        try {
            JsonNode result = future.get(job.timeoutSeconds(), TimeUnit.SECONDS);
            lifecycleService.completeSuccess(
                    job.id(),
                    job.workerId(),
                    objectMapper.writeValueAsString(result)
            );
            return true;
        } catch (TimeoutException exception) {
            future.cancel(true);
            lifecycleService.completeFailure(
                    job.id(),
                    job.workerId(),
                    "Job exceeded timeout of " + job.timeoutSeconds() + " seconds",
                    true
            );
            return true;
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            lifecycleService.completeFailure(
                    job.id(),
                    job.workerId(),
                    cause == null ? exception.getMessage() : cause.getMessage(),
                    false
            );
            return true;
        } catch (Exception exception) {
            lifecycleService.completeFailure(
                    job.id(),
                    job.workerId(),
                    exception.getMessage(),
                    false
            );
            return true;
        }
    }

    private void acknowledge(RecordId recordId) {
        redisTemplate.opsForStream().acknowledge(
                streamKey,
                consumerGroup,
                recordId
        );
    }

    private void pauseAfterReadFailure() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(
                    runnable,
                    prefix + sequence.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        };
    }
}
