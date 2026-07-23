package com.example.jobscheduler.worker;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class FailJobHandler implements JobHandler {

    @Override
    public String jobType() {
        return "FAIL";
    }

    @Override
    public JsonNode execute(JsonNode payload) {
        JsonNode messageNode = payload.get("message");
        String message = messageNode == null
                ? "FAIL handler was requested"
                : messageNode.asString();
        throw new IllegalStateException(message);
    }
}
