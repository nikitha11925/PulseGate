package com.pulsegate.repository;

import com.pulsegate.model.Job;
import com.pulsegate.model.JobStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reactive R2DBC repository for {@link Job}. Everything returns {@code Mono}/{@code Flux};
 * no blocking access anywhere.
 *
 * <p>For the {@code @Query} methods we bind enum values as their {@code name()} String to keep
 * the generated SQL unambiguous against the VARCHAR columns.
 */
public interface JobRepository extends ReactiveCrudRepository<Job, UUID> {

    Flux<Job> findByStatusOrderByCreatedAtDesc(JobStatus status);

    Mono<Long> countByStatus(JobStatus status);

    /** FAILED jobs whose backoff window has elapsed — picked up by the retry scheduler. */
    @Query("""
            SELECT * FROM jobs
            WHERE status = :status
              AND next_retry_at IS NOT NULL
              AND next_retry_at <= :now
            ORDER BY next_retry_at ASC
            LIMIT :limit
            """)
    Flux<Job> findDueForRetry(@Param("status") String status,
                              @Param("now") LocalDateTime now,
                              @Param("limit") int limit);

    /** PROCESSING jobs untouched since {@code threshold} — candidates for reclaim if a worker crashed. */
    @Query("""
            SELECT * FROM jobs
            WHERE status = :status
              AND updated_at < :threshold
            ORDER BY updated_at ASC
            LIMIT :limit
            """)
    Flux<Job> findStuckProcessing(@Param("status") String status,
                                  @Param("threshold") LocalDateTime threshold,
                                  @Param("limit") int limit);

    @Query("SELECT * FROM jobs ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Job> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Job> findPageByStatus(@Param("status") String status,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    @Query("SELECT * FROM jobs WHERE type = :type ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Job> findPageByType(@Param("type") String type,
                             @Param("limit") int limit,
                             @Param("offset") int offset);

    @Query("SELECT * FROM jobs WHERE status = :status AND type = :type ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Job> findPageByStatusAndType(@Param("status") String status,
                                      @Param("type") String type,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);
}
