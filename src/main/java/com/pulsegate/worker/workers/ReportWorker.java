package com.pulsegate.worker.workers;

import com.pulsegate.model.Job;
import com.pulsegate.model.JobType;
import com.pulsegate.worker.Worker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Simulated report generation. ~2s of non-blocking "work" via {@code Mono.delay}.
 */
@Component
@Slf4j
public class ReportWorker implements Worker {

    @Override
    public JobType type() {
        return JobType.REPORT;
    }

    @Override
    public Mono<Void> process(Job job) {
        return Mono.delay(Duration.ofSeconds(2))
                .doOnSubscribe(s -> log.info("[REPORT] generating report for job {}", job.getId()))
                .doOnSuccess(s -> log.info("[REPORT] done for job {}", job.getId()))
                .then();
    }
}
