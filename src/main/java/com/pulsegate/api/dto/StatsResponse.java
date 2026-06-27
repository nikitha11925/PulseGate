package com.pulsegate.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/** Snapshot of queue/worker state for the dashboard ({@code GET /api/stats} and the SSE stream). */
@Value
@Builder
public class StatsResponse {

    long queueDepth;     // PENDING
    long processing;     // PROCESSING
    long done;           // DONE
    long failed;         // FAILED (awaiting retry)
    long dead;           // DEAD (dead-letter)
    long processedTotal; // running total processed since this instance started
    int activeWorkers;   // jobs in flight on this instance right now
    Instant timestamp;
}
