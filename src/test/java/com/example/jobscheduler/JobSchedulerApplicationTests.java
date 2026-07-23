package com.example.jobscheduler;

import com.example.jobscheduler.dto.SubmitJobRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JobSchedulerApplicationTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void deserializesSubmitJobRequestPayload() throws Exception {
        String requestBody = """
                {
                  "jobType": "EMAIL",
                  "payload": {
                    "recipient": "user@example.com",
                    "priority": 1
                  },
                  "maxRetries": 3,
                  "timeoutSeconds": 60
                }
                """;

        SubmitJobRequest request =
                objectMapper.readValue(requestBody, SubmitJobRequest.class);

        assertThat(request.jobType()).isEqualTo("EMAIL");
        assertThat(request.payload().get("recipient").asString())
                .isEqualTo("user@example.com");
        assertThat(request.payload().get("priority").asInt())
                .isEqualTo(1);
    }

}
