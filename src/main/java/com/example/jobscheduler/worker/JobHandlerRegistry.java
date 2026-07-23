package com.example.jobscheduler.worker;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        handler -> normalize(handler.jobType()),
                        Function.identity()
                ));
    }

    public Optional<JobHandler> find(String jobType) {
        return Optional.ofNullable(handlers.get(normalize(jobType)));
    }

    private String normalize(String jobType) {
        return jobType.trim().toUpperCase();
    }
}
