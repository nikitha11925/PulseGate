package com.pulsegate.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;

/**
 * Applies {@code schema.sql} on startup. R2DBC has no Flyway/Liquibase integration, so we use
 * a {@link ConnectionFactoryInitializer} (the reactive equivalent of Spring's schema init) to
 * create the {@code jobs} table and its indexes if they don't already exist.
 */
@Configuration
public class R2dbcConfig {

    @Bean
    public ConnectionFactoryInitializer connectionFactoryInitializer(ConnectionFactory connectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        // schema.sql is written to be idempotent (IF NOT EXISTS), so it is safe on every boot.
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
        return initializer;
    }
}
