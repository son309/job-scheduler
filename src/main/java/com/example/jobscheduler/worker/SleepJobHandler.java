package com.example.jobscheduler.worker;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class SleepJobHandler implements JobHandler {

    private static final long MAX_SLEEP_MILLIS = 300_000;

    @Override
    public String jobType() {
        return "SLEEP";
    }

    @Override
    public JsonNode execute(JsonNode payload) throws InterruptedException {
        JsonNode durationNode = payload.get("durationMs");
        if (durationNode == null || !durationNode.isNumber()) {
            throw new IllegalArgumentException(
                    "SLEEP payload must contain numeric durationMs"
            );
        }

        long durationMillis = durationNode.asLong();
        if (durationMillis < 0 || durationMillis > MAX_SLEEP_MILLIS) {
            throw new IllegalArgumentException(
                    "durationMs must be between 0 and " + MAX_SLEEP_MILLIS
            );
        }

        Thread.sleep(durationMillis);
        return payload.deepCopy();
    }
}
