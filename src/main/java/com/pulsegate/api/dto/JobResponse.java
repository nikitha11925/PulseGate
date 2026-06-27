package com.pulsegate.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulsegate.model.JobStatus;
import com.pulsegate.model.JobType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/** API view of a {@link com.pulsegate.model.Job} (payload rendered back as JSON, not raw text). */
@Value
@Builder
public class JobResponse {

    UUID id;
    JobType type;
    JsonNode payload;
    JobStatus status;
    int priority;
    int attempts;
    int maxAttempts;
    LocalDateTime nextRetryAt;
    String errorMessage;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    LocalDateTime completedAt;
}
