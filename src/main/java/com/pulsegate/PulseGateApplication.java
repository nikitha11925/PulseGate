package com.pulsegate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for PulseGate — a reactive background job processing engine.
 *
 * <p>WHY reactive: the entire service runs on the non-blocking stack (Netty for HTTP,
 * R2DBC for Postgres, Lettuce for Redis). A handful of event-loop threads can therefore
 * keep thousands of jobs in flight at once, because no thread ever sits blocked waiting
 * on I/O. The trade-off is a hard rule enforced across the codebase: no blocking calls
 * (no {@code Thread.sleep}, no {@code .block()}) in production code — simulated work uses
 * {@code Mono.delay} and all persistence/queue access returns {@code Mono}/{@code Flux}.
 */
@SpringBootApplication
@EnableScheduling // retry scheduler, pending-job reclaimer, and queue-depth gauge refresh
public class PulseGateApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseGateApplication.class, args);
    }
}
