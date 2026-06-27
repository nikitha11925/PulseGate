package com.pulsegate.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tags every metric with {@code application=pulsegate} so multiple instances/services scraped by
 * the same Prometheus stay distinguishable. The custom job metrics themselves live in
 * {@link com.pulsegate.metrics.QueueMetrics}.
 */
@Configuration
public class MetricsConfig {

    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry.config().commonTags("application", "pulsegate");
    }
}
