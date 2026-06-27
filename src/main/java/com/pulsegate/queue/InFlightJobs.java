package com.pulsegate.queue;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of jobs currently being processed by THIS instance, so an in-flight job can be
 * interrupted by the cancel API.
 *
 * <p>Each processing job registers a one-shot {@link Sinks.Empty} signal. {@link JobConsumer} races
 * that signal against the worker with {@code Mono.firstWithSignal}: if the signal completes first,
 * Reactor disposes the running work (the worker's {@code Mono.delay} or {@code WebClient} call),
 * and the job is marked {@code CANCELLED}. This is cooperative reactive cancellation — no threads
 * are interrupted; the in-flight {@code Mono} is simply unsubscribed.
 */
@Component
public class InFlightJobs {

    private final Map<UUID, Sinks.Empty<Void>> signals = new ConcurrentHashMap<>();

    /**
     * Register a job as interruptible. The returned Mono completes (empty) when, and only when,
     * cancellation is requested for this job id — never on its own.
     */
    public Mono<Void> track(UUID jobId) {
        Sinks.Empty<Void> sink = Sinks.empty();
        signals.put(jobId, sink);
        return sink.asMono();
    }

    /** Stop tracking the job once processing settles (success, failure, or cancellation). */
    public void clear(UUID jobId) {
        signals.remove(jobId);
    }

    /**
     * Request cancellation of an in-flight job.
     *
     * @return {@code true} if the job was being tracked here (the running worker will be disposed);
     *         {@code false} if it isn't in flight on this instance (caller should fall back).
     */
    public boolean requestCancel(UUID jobId) {
        Sinks.Empty<Void> sink = signals.get(jobId);
        if (sink == null) {
            return false;
        }
        sink.tryEmitEmpty();
        return true;
    }
}
