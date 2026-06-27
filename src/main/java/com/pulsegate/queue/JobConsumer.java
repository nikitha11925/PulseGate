package com.pulsegate.queue;

import com.pulsegate.config.PulseGateProperties;
import com.pulsegate.config.RedisConfig;
import com.pulsegate.metrics.QueueMetrics;
import com.pulsegate.model.Job;
import com.pulsegate.model.JobStatus;
import com.pulsegate.repository.JobRepository;
import com.pulsegate.retry.RetryPolicy;
import com.pulsegate.worker.Worker;
import com.pulsegate.worker.WorkerRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The heart of the engine: continuously reads jobs from the Redis stream consumer group and runs
 * them through the right {@link Worker}, updating Postgres state and metrics along the way.
 *
 * <p><b>Why WebFlux here:</b> the pipeline is one long reactive stream. A handful of event-loop
 * threads drive everything; no thread is ever parked waiting on Redis, Postgres, or a worker's I/O.
 * The single most important line is the second {@code flatMap}'s concurrency argument — that is the
 * backpressure valve that caps how many jobs run at once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobConsumer implements SmartLifecycle {

    private final ReactiveStringRedisTemplate redis;
    private final JobRepository jobRepository;
    private final WorkerRegistry workerRegistry;
    private final RetryPolicy retryPolicy;
    private final DeadLetterHandler deadLetterHandler;
    private final QueueMetrics metrics;
    private final PulseGateProperties properties;
    private final ConsumerIdentity consumerIdentity;
    private final InFlightJobs inFlightJobs;

    private volatile boolean running = false;
    private Disposable subscription;

    @Override
    public void start() {
        int concurrency = properties.getWorker().getConcurrency();
        Duration pollInterval = Duration.ofMillis(properties.getWorker().getPollIntervalMs());
        log.info("Starting JobConsumer '{}' (concurrency={}, pollInterval={}ms)",
                consumerIdentity.getName(), concurrency, pollInterval.toMillis());

        this.subscription = ensureConsumerGroup()
                .thenMany(Flux.interval(Duration.ZERO, pollInterval))
                // If a poll is slow, drop surplus ticks rather than letting them queue up forever.
                .onBackpressureDrop()
                // Serialize polling: keep at most ONE outstanding XREADGROUP in flight.
                .flatMap(tick -> readBatch(concurrency), 1)
                // === BACKPRESSURE ===
                // Process at most `concurrency` jobs at the same time. This one number throttles the
                // whole system — DB writes, worker execution, memory — exactly as the spec intends.
                .flatMap(this::handleRecord, concurrency)
                .onErrorContinue((err, obj) -> log.error("Consumer pipeline error (continuing)", err))
                .subscribe();

        this.running = true;
    }

    /**
     * XGROUP CREATE ... MKSTREAM (Spring's createGroup passes mkStream=true). Idempotent: on restart
     * Redis returns BUSYGROUP, which we swallow.
     */
    private Mono<Void> ensureConsumerGroup() {
        return redis.opsForStream()
                .createGroup(RedisConfig.STREAM, ReadOffset.from("0"), RedisConfig.GROUP)
                .doOnNext(res -> log.info("Created consumer group '{}' on stream '{}'",
                        RedisConfig.GROUP, RedisConfig.STREAM))
                .onErrorResume(e -> {
                    log.debug("Consumer group '{}' already exists: {}", RedisConfig.GROUP, e.getMessage());
                    return Mono.just("OK");
                })
                .then();
    }

    /** XREADGROUP GROUP pulsegate-workers {consumer} COUNT {n} BLOCK 2000 STREAMS pulsegate:jobs &gt; */
    private Flux<MapRecord<String, Object, Object>> readBatch(int count) {
        StreamReadOptions options = StreamReadOptions.empty()
                .count(count)
                .block(Duration.ofSeconds(2)); // server-side long-poll; not a thread block
        return redis.opsForStream()
                .read(Consumer.from(RedisConfig.GROUP, consumerIdentity.getName()),
                        options,
                        StreamOffset.create(RedisConfig.STREAM, ReadOffset.lastConsumed()))
                .onErrorResume(e -> {
                    log.error("XREADGROUP failed", e);
                    return Flux.empty();
                });
    }

    private Mono<Void> handleRecord(MapRecord<String, Object, Object> record) {
        RecordId messageId = record.getId();
        String jobIdRaw = String.valueOf(record.getValue().get("job_id"));
        final UUID jobId;
        try {
            jobId = UUID.fromString(jobIdRaw);
        } catch (IllegalArgumentException ex) {
            log.error("Malformed job_id '{}' in message {} — acking to discard", jobIdRaw, messageId);
            return acknowledge(messageId);
        }

        return jobRepository.findById(jobId)
                // thenReturn(TRUE) so this branch emits a value — otherwise the empty Mono<Void> from
                // processJob would (wrongly) trigger switchIfEmpty below.
                .flatMap(job -> {
                    // Cancelled while still queued (PENDING -> CANCELLED via the cancel API): the
                    // stream message is still here, but there's nothing to run. Drop the delivery.
                    if (job.getStatus() == JobStatus.CANCELLED) {
                        log.info("Job {} was cancelled before pickup — acking", job.getId());
                        return acknowledge(messageId).thenReturn(Boolean.TRUE);
                    }
                    return processJob(job, messageId).thenReturn(Boolean.TRUE);
                })
                // Genuinely no DB row (cancelled/deleted): orphan message, ack and move on.
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No job row for {} (orphan message {}) — acking", jobId, messageId);
                    return acknowledge(messageId).thenReturn(Boolean.TRUE);
                }))
                .onErrorResume(err -> {
                    // Bookkeeping failure shouldn't wedge the stream; ack and continue.
                    log.error("Unexpected error handling job {} (message {}) — acking", jobId, messageId, err);
                    return acknowledge(messageId).thenReturn(Boolean.TRUE);
                })
                .then();
    }

    private Mono<Void> processJob(Job job, RecordId messageId) {
        Worker worker = workerRegistry.forType(job.getType()).orElse(null);
        if (worker == null) {
            // Nothing can ever handle this type — fail it straight to dead-letter (no retries).
            return onFailure(job, messageId,
                    new IllegalStateException("No worker registered for type " + job.getType()), true);
        }

        LocalDateTime now = LocalDateTime.now();
        job.setStatus(JobStatus.PROCESSING);
        job.setUpdatedAt(now);

        Timer.Sample sample = metrics.startSample();
        metrics.workerStarted();

        // Register this job as interruptible. cancelSignal completes only if the cancel API is
        // called for it; racing it against the worker disposes the in-flight work on cancel.
        Mono<Void> cancelSignal = inFlightJobs.track(job.getId());

        return jobRepository.save(job)
                .then(Mono.defer(() -> Mono.firstWithSignal(
                        // FALSE: worker ran to completion. TRUE: a cancel beat it to the finish,
                        // and firstWithSignal disposes the still-running worker Mono.
                        worker.process(job).thenReturn(Boolean.FALSE),
                        cancelSignal.thenReturn(Boolean.TRUE))))
                .flatMap(cancelled -> Boolean.TRUE.equals(cancelled)
                        ? onCancelled(job, messageId, sample)
                        : onSuccess(job, messageId, sample))
                .onErrorResume(err -> {
                    metrics.stopSample(sample, job.getType());
                    return onFailure(job, messageId, err, false);
                })
                .doFinally(signal -> {
                    inFlightJobs.clear(job.getId());
                    metrics.workerFinished();
                });
    }

    private Mono<Void> onSuccess(Job job, RecordId messageId, Timer.Sample sample) {
        // Mono.defer so the timestamp and field mutations run when this stage is actually
        // SUBSCRIBED (i.e. after worker.process() completes), not eagerly at assembly time.
        // Without the defer, LocalDateTime.now() is captured the instant the pipeline is built,
        // making completed_at/updated_at reflect submission time and every job look instant.
        return Mono.defer(() -> {
            LocalDateTime now = LocalDateTime.now();
            job.setStatus(JobStatus.DONE);
            job.setUpdatedAt(now);
            job.setCompletedAt(now);
            job.setErrorMessage(null);
            return jobRepository.save(job)
                    .then(acknowledge(messageId))
                    .doOnSuccess(v -> {
                        metrics.stopSample(sample, job.getType());
                        metrics.recordProcessed(job.getType(), JobStatus.DONE);
                        log.info("Job {} ({}) DONE", job.getId(), job.getType());
                    });
        });
    }

    /**
     * The worker was interrupted by a cancel request. Like {@link #onSuccess}, the field mutations
     * run inside {@code Mono.defer} so the timestamp reflects the moment of cancellation. The job is
     * terminal (no retry); we ack the delivery so it isn't redelivered.
     */
    private Mono<Void> onCancelled(Job job, RecordId messageId, Timer.Sample sample) {
        return Mono.defer(() -> {
            LocalDateTime now = LocalDateTime.now();
            job.setStatus(JobStatus.CANCELLED);
            job.setUpdatedAt(now);
            job.setCompletedAt(now);
            return jobRepository.save(job)
                    .then(acknowledge(messageId))
                    .doOnSuccess(v -> {
                        metrics.stopSample(sample, job.getType());
                        log.info("Job {} ({}) CANCELLED mid-flight", job.getId(), job.getType());
                    });
        });
    }

    private Mono<Void> onFailure(Job job, RecordId messageId, Throwable error, boolean forceDead) {
        int newAttempts = job.getAttempts() + 1;
        job.setAttempts(newAttempts);
        String message = error.getMessage() != null ? error.getMessage() : error.toString();

        boolean exhausted = forceDead || newAttempts >= job.getMaxAttempts();
        if (exhausted) {
            return deadLetterHandler.moveToDeadLetter(job, message)
                    .then(acknowledge(messageId))
                    .doOnSuccess(v -> metrics.recordProcessed(job.getType(), JobStatus.DEAD));
        }

        Duration delay = retryPolicy.getDelay(newAttempts);
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(message);
        job.setUpdatedAt(now);
        job.setNextRetryAt(now.plus(delay));
        return jobRepository.save(job)
                // Ack this delivery; RetryScheduler re-enqueues a fresh message once next_retry_at
                // elapses. Redis pending entries can't express "redeliver in 25s", so the backoff
                // schedule lives in Postgres.
                .then(acknowledge(messageId))
                .doOnSuccess(v -> {
                    metrics.recordProcessed(job.getType(), JobStatus.FAILED);
                    log.warn("Job {} ({}) FAILED attempt {}/{} — retry in {}s: {}",
                            job.getId(), job.getType(), newAttempts, job.getMaxAttempts(),
                            delay.getSeconds(), message);
                });
    }

    private Mono<Void> acknowledge(RecordId messageId) {
        return redis.opsForStream()
                .acknowledge(RedisConfig.STREAM, RedisConfig.GROUP, messageId.getValue())
                .then();
    }

    // --- SmartLifecycle ------------------------------------------------------

    @Override
    public void stop() {
        running = false;
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        log.info("Stopped JobConsumer '{}'", consumerIdentity.getName());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Start late (after web/data infra is up) and stop early on shutdown.
        return Integer.MAX_VALUE - 100;
    }
}
