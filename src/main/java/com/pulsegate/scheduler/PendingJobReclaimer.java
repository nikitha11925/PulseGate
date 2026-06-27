package com.pulsegate.scheduler;

import com.pulsegate.config.PulseGateProperties;
import com.pulsegate.config.RedisConfig;
import com.pulsegate.model.JobStatus;
import com.pulsegate.queue.JobProducer;
import com.pulsegate.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Recovers jobs from crashed workers. A worker that dies mid-job leaves its stream message
 * "pending" (delivered but never acked). Every minute this scans XPENDING for deliveries idle
 * longer than the stuck threshold, recovers the job_id from the message body, re-enqueues the job,
 * and acks the stale delivery.
 *
 * <p>The spec describes XCLAIM; we instead re-enqueue (XADD) + XACK, which is simpler and yields
 * the same at-least-once outcome without juggling consumer ownership.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingJobReclaimer {

    private final ReactiveStringRedisTemplate redis;
    private final JobRepository jobRepository;
    private final JobProducer jobProducer;
    private final PulseGateProperties properties;

    @Scheduled(fixedDelayString = "${pulsegate.reclaimer.interval-ms:60000}")
    public void reclaimStuckJobs() {
        Duration threshold = Duration.ofMinutes(properties.getReclaimer().getStuckThresholdMinutes());
        redis.opsForStream()
                .pending(RedisConfig.STREAM, RedisConfig.GROUP, Range.unbounded(), 100)
                .flatMapMany(Flux::fromIterable)
                .filter(pm -> pm.getElapsedTimeSinceLastDelivery().compareTo(threshold) > 0)
                .flatMap(this::reclaimMessage)
                .onErrorContinue((err, obj) -> log.error("Reclaim error", err))
                .subscribe();
    }

    private Mono<Void> reclaimMessage(PendingMessage pending) {
        RecordId messageId = pending.getId();
        long idleSeconds = pending.getElapsedTimeSinceLastDelivery().getSeconds();
        return redis.opsForStream()
                .range(RedisConfig.STREAM, Range.just(messageId.getValue()))
                .next()
                .flatMap(record -> recoverJob(record, messageId, idleSeconds))
                // Message body already gone (trimmed/acked elsewhere): just ack to clean up.
                .switchIfEmpty(acknowledge(messageId).thenReturn(Boolean.TRUE))
                .then();
    }

    private Mono<Boolean> recoverJob(MapRecord<String, Object, Object> record, RecordId messageId, long idleSeconds) {
        String jobIdRaw = String.valueOf(record.getValue().get("job_id"));
        final UUID jobId;
        try {
            jobId = UUID.fromString(jobIdRaw);
        } catch (IllegalArgumentException e) {
            return acknowledge(messageId).thenReturn(Boolean.TRUE);
        }
        return jobRepository.findById(jobId)
                .flatMap(job -> {
                    if (job.getStatus() == JobStatus.PROCESSING) {
                        job.setStatus(JobStatus.PENDING);
                        job.setUpdatedAt(LocalDateTime.now());
                        log.warn("Reclaiming stuck job {} (idle {}s, message {})", jobId, idleSeconds, messageId);
                        return jobRepository.save(job)
                                .flatMap(jobProducer::enqueue)
                                .then(acknowledge(messageId))
                                .thenReturn(Boolean.TRUE);
                    }
                    // The original worker actually finished after all — drop the stale delivery.
                    return acknowledge(messageId).thenReturn(Boolean.TRUE);
                })
                // No job row for this message — orphan; ack it away.
                .switchIfEmpty(acknowledge(messageId).thenReturn(Boolean.TRUE));
    }

    private Mono<Void> acknowledge(RecordId messageId) {
        return redis.opsForStream()
                .acknowledge(RedisConfig.STREAM, RedisConfig.GROUP, messageId.getValue())
                .then();
    }
}
