package com.pulsegate.metrics;

import com.pulsegate.model.JobStatus;
import com.pulsegate.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes the {@code pulsegate_queue_depth} gauge from Postgres so Prometheus sees an
 * accurate PENDING count even when no dashboard/SSE client is connected. We subscribe (non-blocking
 * fire-and-forget) rather than block the scheduler thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueueDepthRefresher {

    private final JobRepository jobRepository;
    private final QueueMetrics metrics;

    @Scheduled(fixedDelayString = "${pulsegate.metrics.queue-depth-refresh-ms:3000}")
    public void refresh() {
        jobRepository.countByStatus(JobStatus.PENDING)
                .subscribe(metrics::setQueueDepth,
                        err -> log.debug("queue depth refresh failed: {}", err.getMessage()));
    }
}
