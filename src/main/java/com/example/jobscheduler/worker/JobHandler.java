package com.example.jobscheduler.worker;

import tools.jackson.databind.JsonNode;

public interface JobHandler {

    String jobType();

    JsonNode execute(JsonNode payload) throws Exception;
}
