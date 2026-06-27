package com.pulsegate.worker;

import com.pulsegate.model.JobType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps each {@link JobType} to its {@link Worker}. Spring injects every {@code Worker} bean, so
 * adding a new job type is just adding a new {@code @Component} that implements {@code Worker} —
 * no wiring changes here.
 */
@Component
public class WorkerRegistry {

    private final Map<JobType, Worker> workers;

    public WorkerRegistry(List<Worker> workerBeans) {
        this.workers = workerBeans.stream()
                .collect(Collectors.toMap(Worker::type, Function.identity()));
    }

    public Optional<Worker> forType(JobType type) {
        return Optional.ofNullable(workers.get(type));
    }
}
