package com.pulsegate.worker.workers;

import com.pulsegate.model.Job;
import com.pulsegate.model.JobType;
import com.pulsegate.worker.Worker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Simulated image resize. ~1s of non-blocking "work" via {@code Mono.delay}.
 */
@Component
@Slf4j
public class ImageResizeWorker implements Worker {

    @Override
    public JobType type() {
        return JobType.IMAGE_RESIZE;
    }

    @Override
    public Mono<Void> process(Job job) {
        return Mono.delay(Duration.ofSeconds(1))
                .doOnSubscribe(s -> log.info("[IMAGE_RESIZE] resizing image for job {}", job.getId()))
                .doOnSuccess(s -> log.info("[IMAGE_RESIZE] done for job {}", job.getId()))
                .then();
    }
}
