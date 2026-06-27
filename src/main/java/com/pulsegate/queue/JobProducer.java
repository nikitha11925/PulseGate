package com.pulsegate.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsegate.config.PulseGateProperties;
import com.pulsegate.config.RedisConfig;
import com.pulsegate.model.Job;
import com.pulsegate.model.JobStatus;
import com.pulsegate.model.JobType;
import com.pulsegate.repository.JobRepository;
import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Pushes jobs onto the queue. The submission contract is "persist then publish, return fast":
 * <ol>
 *   <li>Save the job as PENDING in Postgres (the durable source of truth).</li>
 *   <li>XADD a tiny message ({@code job_id} + {@code type}) to the Redis stream so a worker can pick it up.</li>
 * </ol>
 * The HTTP caller gets the job id back immediately; the actual work happens asynchronously.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobProducer {

    private final JobRepository jobRepository;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final PulseGateProperties properties;

    public Mono<Job> submit(JobType type, JsonNode payload, Integer priority) {
        return Mono.fromCallable(() -> buildPending(type, payload, priority))
                .flatMap(jobRepository::save)
                .flatMap(saved -> enqueue(saved).thenReturn(saved))
                .doOnNext(saved -> log.info("Submitted job {} ({})", saved.getId(), saved.getType()));
    }

    private Job buildPending(JobType type, JsonNode payload, Integer priority) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        return Job.builder()
                .type(type)
                // Serialize the arbitrary JSON payload to text, wrapped as the driver's Json type
                // so it binds to the jsonb column.
                .payload(Json.of(objectMapper.writeValueAsString(payload)))
                .status(JobStatus.PENDING)
                .priority(priority != null ? priority : 5)
                .attempts(0)
                .maxAttempts(properties.getRetry().getMaxAttempts())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * XADD pulsegate:jobs * job_id {uuid} type {type}. Shared by initial submission, the retry
     * scheduler, the reclaimer, and manual dead-letter retries.
     */
    public Mono<RecordId> enqueue(Job job) {
        Map<String, String> body = Map.of(
                "job_id", job.getId().toString(),
                "type", job.getType().name());
        return redis.opsForStream()
                .add(StreamRecords.newRecord().in(RedisConfig.STREAM).ofMap(body))
                .doOnNext(id -> log.debug("Enqueued job {} as stream message {}", job.getId(), id));
    }
}
