package com.pulsegate.metrics;

import com.pulsegate.model.JobStatus;
import com.pulsegate.model.JobType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central holder for PulseGate's Prometheus metrics. Exposed via Micrometer at
 * {@code /actuator/prometheus}.
 *
 * <p>Naming note: Micrometer's Prometheus registry appends {@code _total} to counters and a
 * {@code _seconds} unit suffix to timers. So the base names below ("pulsegate_jobs_processed",
 * "pulsegate_dead_letter", "pulsegate_job_processing") surface in the scrape as
 * {@code pulsegate_jobs_processed_total}, {@code pulsegate_dead_letter_total} and
 * {@code pulsegate_job_processing_seconds} respectively — matching the spec.
 */
@Component
public class QueueMetrics {

    private final MeterRegistry registry;

    /** Backing store for the queue-depth gauge; refreshed from Postgres by a scheduled task. */
    private final AtomicLong queueDepth = new AtomicLong(0);
    /** Jobs currently being processed by this instance. */
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    /** Convenience running total surfaced via /api/stats (counters aren't readable back out). */
    private final AtomicLong processedTotal = new AtomicLong(0);

    public QueueMetrics(MeterRegistry registry) {
        this.registry = registry;

        Gauge.builder("pulsegate_queue_depth", queueDepth, AtomicLong::doubleValue)
                .description("Current number of PENDING jobs waiting in the queue")
                .register(registry);

        Gauge.builder("pulsegate_worker_active_count", activeWorkers, AtomicInteger::doubleValue)
                .description("Jobs currently being processed by this instance")
                .register(registry);
    }

    // --- queue depth ---------------------------------------------------------
    public void setQueueDepth(long depth) {
        queueDepth.set(depth);
    }

    public long getQueueDepth() {
        return queueDepth.get();
    }

    // --- active workers (in-flight jobs) -------------------------------------
    public void workerStarted() {
        activeWorkers.incrementAndGet();
    }

    public void workerFinished() {
        activeWorkers.decrementAndGet();
    }

    public int getActiveWorkers() {
        return activeWorkers.get();
    }

    // --- processed counter (by type + terminal status) -----------------------
    public void recordProcessed(JobType type, JobStatus status) {
        processedTotal.incrementAndGet();
        registry.counter("pulsegate_jobs_processed", "type", type.name(), "status", status.name()).increment();
    }

    public long getProcessedTotal() {
        return processedTotal.get();
    }

    // --- dead-letter counter -------------------------------------------------
    public void incrementDeadLetter(JobType type) {
        registry.counter("pulsegate_dead_letter", "type", type.name()).increment();
    }

    // --- processing latency timer (p50/p95/p99) ------------------------------
    public Timer.Sample startSample() {
        return Timer.start(registry);
    }

    public void stopSample(Timer.Sample sample, JobType type) {
        sample.stop(Timer.builder("pulsegate_job_processing")
                .tag("type", type.name())
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry));
    }
}
