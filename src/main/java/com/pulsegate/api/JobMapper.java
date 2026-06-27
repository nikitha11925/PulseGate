package com.pulsegate.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsegate.api.dto.JobResponse;
import com.pulsegate.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Converts the {@link Job} entity (jsonb payload as driver Json) into the API {@link JobResponse}. */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobMapper {

    private final ObjectMapper objectMapper;

    public JobResponse toResponse(Job job) {
        JsonNode payload = null;
        try {
            if (job.getPayload() != null) {
                payload = objectMapper.readTree(job.getPayload().asString());
            }
        } catch (Exception e) {
            log.warn("Could not parse payload for job {}: {}", job.getId(), e.getMessage());
        }
        return JobResponse.builder()
                .id(job.getId())
                .type(job.getType())
                .payload(payload)
                .status(job.getStatus())
                .priority(job.getPriority())
                .attempts(job.getAttempts())
                .maxAttempts(job.getMaxAttempts())
                .nextRetryAt(job.getNextRetryAt())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}
