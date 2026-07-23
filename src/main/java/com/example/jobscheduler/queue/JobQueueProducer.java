package com.example.jobscheduler.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JobQueueProducer {

    private final StringRedisTemplate redisTemplate;

    @Value("${job-queue.stream-key}")
    private String streamKey;

    public String enqueue(UUID jobId) {
        Map<String, String> message = Map.of(
                QueueConstants.JOB_ID_FIELD,
                jobId.toString()
        );

        MapRecord<String, String, String> record =
                StreamRecords
                        .string(message)
                        .withStreamKey(streamKey);

        RecordId recordId =
                redisTemplate.opsForStream().add(record);

        if (recordId == null) {
            throw new IllegalStateException(
                    "Could not enqueue job: " + jobId
            );
        }

        return recordId.getValue();
    }
}