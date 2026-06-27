package com.pulsegate.model;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC entity mapped to the {@code jobs} table.
 *
 * <p>Mapping notes:
 * <ul>
 *   <li>{@code type} / {@code status} are Java enums; Spring Data R2DBC stores them as their
 *       {@code name()} in the VARCHAR columns automatically.</li>
 *   <li>{@code payload} uses the driver's {@link Json} type so it maps cleanly to the
 *       {@code jsonb} column without a custom converter (and without accidentally turning
 *       every other String field into JSON).</li>
 *   <li>Multi-word columns are annotated with {@link Column} because Spring Data R2DBC does
 *       not snake_case property names by default.</li>
 *   <li>{@code id} is left null on insert so Postgres' {@code gen_random_uuid()} default
 *       generates it; the value is read back via {@code RETURNING}.</li>
 * </ul>
 */
@Table("jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Job {

    @Id
    private UUID id;

    private JobType type;

    private Json payload;

    private JobStatus status;

    private int priority;

    private int attempts;

    @Column("max_attempts")
    private int maxAttempts;

    @Column("next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column("error_message")
    private String errorMessage;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("completed_at")
    private LocalDateTime completedAt;
}
