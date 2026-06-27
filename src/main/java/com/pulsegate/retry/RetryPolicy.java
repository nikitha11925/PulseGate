package com.pulsegate.retry;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Exponential backoff policy: the delay before the Nth retry is {@code 5^N} seconds.
 * <pre>
 *   attempt 1 -> 5s
 *   attempt 2 -> 25s
 *   attempt 3 -> 125s
 * </pre>
 * The maximum number of attempts is configured separately ({@code pulsegate.retry.max-attempts}).
 */
@Component
public class RetryPolicy {

    public Duration getDelay(int attempt) {
        return Duration.ofSeconds((long) Math.pow(5, attempt));
    }
}
