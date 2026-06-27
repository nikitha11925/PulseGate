package com.pulsegate.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed binding for the {@code pulsegate.*} settings in application.yml.
 * Defaults here mirror the documented defaults so the app is runnable with no env vars.
 */
@Component
@ConfigurationProperties(prefix = "pulsegate")
@Getter
@Setter
public class PulseGateProperties {

    private final Worker worker = new Worker();
    private final Retry retry = new Retry();
    private final Reclaimer reclaimer = new Reclaimer();

    @Getter
    @Setter
    public static class Worker {
        /** Max concurrent in-flight jobs per instance — the WebFlux backpressure limit. */
        private int concurrency = 10;
        /** How often each instance polls Redis for new work. */
        private long pollIntervalMs = 500;
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 3;
        /** How often the retry scheduler re-enqueues FAILED jobs whose backoff has elapsed. */
        private long pollMs = 5000;
    }

    @Getter
    @Setter
    public static class Reclaimer {
        /** A PROCESSING job idle longer than this is treated as a crashed worker. */
        private int stuckThresholdMinutes = 5;
        /** How often the reclaimer runs. */
        private long intervalMs = 60000;
    }
}
