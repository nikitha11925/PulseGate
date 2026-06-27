package com.pulsegate.scheduler;

import com.pulsegate.model.JobStatus;
import com.pulsegate.queue.JobProducer;
import com.pulsegate.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Makes exponential backoff actually happen. When a job fails (but still has retries left) the
 * consumer parks it as FAILED with a future {@code next_retry_at}. This scheduler periodically
 * flips any FAILED job whose backoff window has elapsed back to PENDING and re-publishes it to the
 * stream so a worker picks it up again.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetryScheduler {

    private final JobRepository jobRepository;
    private final JobProducer jobProducer;

    @Scheduled(fixedDelayString = "${pulsegate.retry.poll-ms:5000}")
    public void requeueDueRetries() {
        jobRepository.findDueForRetry(JobStatus.FAILED.name(), LocalDateTime.now(), 50)
                .flatMap(job -> {
                    job.setStatus(JobStatus.PENDING);
                    job.setNextRetryAt(null);
                    job.setUpdatedAt(LocalDateTime.now());
                    return jobRepository.save(job)
                            .flatMap(saved -> jobProducer.enqueue(saved).thenReturn(saved))
                            .doOnNext(saved -> log.info("Re-enqueued job {} for retry (attempt {})",
                                    saved.getId(), saved.getAttempts()));
                })
                .onErrorContinue((err, obj) -> log.error("Retry re-enqueue error", err))
                .subscribe();
    }
}
