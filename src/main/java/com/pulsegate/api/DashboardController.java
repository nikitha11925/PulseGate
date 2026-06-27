package com.pulsegate.api;

import com.pulsegate.api.dto.JobResponse;
import com.pulsegate.api.dto.StatsResponse;
import com.pulsegate.metrics.QueueMetrics;
import com.pulsegate.model.JobStatus;
import com.pulsegate.queue.DeadLetterHandler;
import com.pulsegate.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Read APIs that back the dashboard: aggregate stats (one-shot + SSE stream) and dead-letter
 * inspection/retry.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final JobRepository jobRepository;
    private final QueueMetrics metrics;
    private final DeadLetterHandler deadLetterHandler;
    private final JobMapper jobMapper;

    /** One-shot stats snapshot. */
    @GetMapping("/stats")
    public Mono<StatsResponse> stats() {
        return buildStats();
    }

    /** Server-Sent Events: push a fresh stats snapshot every 2 seconds. The dashboard's EventSource
     *  consumes this to update charts without polling. */
    @GetMapping(value = "/stats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StatsResponse>> statsStream() {
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(2))
                .flatMap(tick -> buildStats())
                .map(stats -> ServerSentEvent.builder(stats).event("stats").build());
    }

    private Mono<StatsResponse> buildStats() {
        return Mono.zip(
                        jobRepository.countByStatus(JobStatus.PENDING),
                        jobRepository.countByStatus(JobStatus.PROCESSING),
                        jobRepository.countByStatus(JobStatus.DONE),
                        jobRepository.countByStatus(JobStatus.FAILED),
                        jobRepository.countByStatus(JobStatus.DEAD))
                .map(counts -> {
                    long pending = counts.getT1();
                    metrics.setQueueDepth(pending); // keep the gauge aligned with the DB
                    return StatsResponse.builder()
                            .queueDepth(pending)
                            .processing(counts.getT2())
                            .done(counts.getT3())
                            .failed(counts.getT4())
                            .dead(counts.getT5())
                            .processedTotal(metrics.getProcessedTotal())
                            .activeWorkers(metrics.getActiveWorkers())
                            .timestamp(Instant.now())
                            .build();
                });
    }

    /** List dead-letter (DEAD) jobs, newest first. */
    @GetMapping("/dead-letter")
    public Flux<JobResponse> deadLetter() {
        return jobRepository.findByStatusOrderByCreatedAtDesc(JobStatus.DEAD)
                .map(jobMapper::toResponse);
    }

    /** Manually retry a dead job. */
    @PostMapping("/dead-letter/{id}/retry")
    public Mono<ResponseEntity<JobResponse>> retryDeadJob(@PathVariable UUID id) {
        return deadLetterHandler.retry(id)
                .map(jobMapper::toResponse)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
