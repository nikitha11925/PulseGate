package com.pulsegate.config;

/**
 * Redis Streams configuration constants.
 *
 * <p>The actual reactive connection ({@code ReactiveStringRedisTemplate} backed by Lettuce) is
 * auto-configured by Spring Boot from {@code spring.data.redis.url}, so there is no template
 * bean to declare here. The consumer group is created on startup by
 * {@link com.pulsegate.queue.JobConsumer} (so it is guaranteed to exist before the first read),
 * and each instance's unique consumer name is provided by
 * {@link com.pulsegate.queue.ConsumerIdentity}.
 *
 * <p>This is a pure constants holder, not a Spring {@code @Configuration} class: it declares no
 * {@code @Bean} methods, so annotating it {@code @Configuration} would only force Spring to create
 * a CGLIB proxy subclass — which fails because the constructor is private ("No visible
 * constructors"). Leaving it un-annotated keeps the private-constructor constants idiom valid.
 */
public final class RedisConfig {

    /** Main work stream. Producers XADD here; the consumer group reads from it. */
    public static final String STREAM = "pulsegate:jobs";

    /** Consumer group shared by all worker instances (competing consumers). */
    public static final String GROUP = "pulsegate-workers";

    /** Audit stream that permanently-failed jobs are copied to for inspection. */
    public static final String DEAD_STREAM = "pulsegate:jobs:dead";

    private RedisConfig() {
        // constants holder
    }
}
