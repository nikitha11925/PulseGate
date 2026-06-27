package com.pulsegate.queue;

import com.pulsegate.config.RedisConfig;
import com.pulsegate.metrics.QueueMetrics;
import com.pulsegate.model.Job;
import com.pulsegate.model.JobStatus;
import com.pulsegate.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Handles jobs that have permanently failed (exhausted their retries) and supports manually
 * resurrecting them. "Moving to the dead letter" means: flip status to DEAD in Postgres, bump the
 * dead-letter metric, and copy a record onto an audit stream for inspection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterHandler {

    private final JobRepository jobRepository;
    private final QueueMetrics metrics;
    private final ReactiveStringRedisTemplate redis;
    private final JobProducer jobProducer;

    public Mono<Job> moveToDeadLetter(Job job, String error) {
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(JobStatus.DEAD);
        job.setErrorMessage(error);
        job.setUpdatedAt(now);
        job.setCompletedAt(now);
        return jobRepository.save(job)
                .doOnNext(saved -> {
                    metrics.incrementDeadLetter(saved.getType());
                    log.warn("[DLQ] job {} ({}) is DEAD after {} attempts: {}",
                            saved.getId(), saved.getType(), saved.getAttempts(), error);
                })
                .flatMap(saved -> auditToStream(saved, error).thenReturn(saved));
    }

    private Mono<Void> auditToStream(Job job, String error) {
        Map<String, String> body = Map.of(
                "job_id", job.getId().toString(),
                "type", job.getType().name(),
                "error", error == null ? "" : error);
        return redis.opsForStream()
                .add(StreamRecords.newRecord().in(RedisConfig.DEAD_STREAM).ofMap(body))
                .doOnError(e -> log.error("Failed to write dead-letter audit record for {}", job.getId(), e))
                .onErrorResume(e -> Mono.empty()) // audit failure must never break the pipeline
                .then();
    }

    /** Manually requeue a DEAD job: reset its counters and push it back through the normal flow. */
    public Mono<Job> retry(UUID id) {
        return jobRepository.findById(id)
                .flatMap(job -> {
                    LocalDateTime now = LocalDateTime.now();
                    job.setStatus(JobStatus.PENDING);
                    job.setAttempts(0);
                    job.setErrorMessage(null);
                    job.setNextRetryAt(null);
                    job.setCompletedAt(null);
                    job.setUpdatedAt(now);
                    return jobRepository.save(job)
                            .flatMap(saved -> jobProducer.enqueue(saved).thenReturn(saved))
                            .doOnNext(saved -> log.info("[DLQ] manually requeued job {}", saved.getId()));
                });
    }
}
