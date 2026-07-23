package com.example.jobscheduler.queue;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisStreamInitializer {

    private final StringRedisTemplate redisTemplate;

    @Value("${job-queue.stream-key}")
    private String streamKey;

    @Value("${job-queue.consumer-group}")
    private String consumerGroup;

    @PostConstruct
    public void initialize() {
        createStreamIfMissing();
        createConsumerGroupIfMissing();
    }

    private void createStreamIfMissing() {
        Boolean exists = redisTemplate.hasKey(streamKey);

        if (Boolean.FALSE.equals(exists)) {
            redisTemplate.opsForStream().add(
                    streamKey,
                    Map.of("type", "bootstrap")
            );
        }
    }

    private void createConsumerGroupIfMissing() {
        try {
            redisTemplate.opsForStream().createGroup(
                    streamKey,
                    ReadOffset.from("0"),
                    consumerGroup
            );
        } catch (RedisSystemException exception) {
            /*
             * BUSYGROUP nghĩa là group đã tồn tại.
             * Trường hợp này không phải lỗi.
             */
            if (!isConsumerGroupAlreadyExists(exception)) {
                throw exception;
            }
        }
    }

    private boolean isConsumerGroupAlreadyExists(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            String message = cause.getMessage();

            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}
