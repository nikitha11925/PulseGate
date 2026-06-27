package com.pulsegate.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulsegate.model.JobType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for {@code POST /api/jobs}.
 * <pre>{ "type": "EMAIL", "payload": { "to": "user@example.com" }, "priority": 5 }</pre>
 */
@Data
public class JobSubmitRequest {

    @NotNull
    private JobType type;

    @NotNull
    private JsonNode payload;

    /** Optional; defaults to 5 when omitted. */
    private Integer priority;
}
