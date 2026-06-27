package com.pulsegate.model;

/**
 * Lifecycle of a job.
 *
 * <pre>
 *   PENDING    -> queued, waiting for a worker to pick it up
 *   PROCESSING -> claimed by a worker, currently running
 *   DONE       -> finished successfully
 *   FAILED     -> failed but still has retries left (scheduled for a later attempt)
 *   DEAD       -> exhausted all retries; moved to the dead-letter queue
 *   CANCELLED  -> cancelled by the user while PENDING or PROCESSING (no further work/retries)
 * </pre>
 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED,
    DEAD,
    CANCELLED
}
