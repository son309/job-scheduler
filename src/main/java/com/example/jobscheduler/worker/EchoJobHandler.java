package com.example.jobscheduler.worker;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class EchoJobHandler implements JobHandler {

    @Override
    public String jobType() {
        return "ECHO";
    }

    @Override
    public JsonNode execute(JsonNode payload) {
        return payload.deepCopy();
    }
}
