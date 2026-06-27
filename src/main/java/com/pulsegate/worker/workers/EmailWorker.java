package com.pulsegate.worker.workers;

import com.pulsegate.model.Job;
import com.pulsegate.model.JobType;
import com.pulsegate.worker.Worker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Simulated email sender. Uses {@code Mono.delay} (NOT {@code Thread.sleep}) so the "work" is
 * scheduled on a timer without parking an event-loop thread.
 */
@Component
@Slf4j
public class EmailWorker implements Worker {

    @Override
    public JobType type() {
        return JobType.EMAIL;
    }

    @Override
    public Mono<Void> process(Job job) {
        return Mono.delay(Duration.ofMillis(500))
                .doOnSubscribe(s -> log.info("[EMAIL] sending email for job {}", job.getId()))
                .doOnSuccess(s -> log.info("[EMAIL] sent for job {}", job.getId()))
                .then();
    }
}
