package com.pulsegate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux-level configuration. Adds permissive CORS for local development so the Vite dashboard
 * can call the API and the SSE stream directly. The dashboard's host port varies between setups
 * (5173 by default, but remapped when another local service holds that port), so we allow any
 * localhost origin via a pattern rather than pinning specific ports. In a containerized deployment
 * the dashboard is served same-origin through a proxy, so this is dev-only convenience.
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // allowedOriginPatterns (not allowedOrigins) so the "*" port wildcard is honored.
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
