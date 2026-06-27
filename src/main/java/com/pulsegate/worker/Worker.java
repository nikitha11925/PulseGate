package com.pulsegate.worker;

import com.pulsegate.model.Job;
import com.pulsegate.model.JobType;
import reactor.core.publisher.Mono;

/**
 * A unit of work for a single {@link JobType}. Implementations MUST be non-blocking: simulated
 * work uses {@code Mono.delay}, real work uses reactive clients (e.g. WebClient). A returned
 * error signal triggers the retry/dead-letter machinery in the consumer.
 */
public interface Worker {

    /** The job type this worker handles. */
    JobType type();

    /** Process the job. Complete = success; error = failure (eligible for retry). */
    Mono<Void> process(Job job);
}
