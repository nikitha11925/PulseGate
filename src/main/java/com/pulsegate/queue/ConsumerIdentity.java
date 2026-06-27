package com.pulsegate.queue;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * Provides this instance's unique Redis Streams consumer name: {@code worker-{hostname}-{uuid}}.
 * Within the shared {@code pulsegate-workers} group, distinct consumer names let Redis track which
 * instance holds which pending message — essential for the reclaimer to find crashed workers' jobs.
 */
@Component
public class ConsumerIdentity {

    private final String name;

    public ConsumerIdentity() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        this.name = "worker-" + host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String getName() {
        return name;
    }
}
