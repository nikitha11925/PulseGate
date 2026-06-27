package com.pulsegate.worker.workers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsegate.model.Job;
import com.pulsegate.model.JobType;
import com.pulsegate.worker.Worker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * The one "real" worker: it performs an actual HTTP POST to the URL in the job payload using the
 * reactive {@link WebClient} (non-blocking). Payload shape:
 * <pre>{ "url": "https://...", "body": { ...optional JSON... } }</pre>
 * A non-2xx response or timeout surfaces as an error, which feeds the retry/backoff machinery.
 */
@Component
@Slf4j
public class WebhookWorker implements Worker {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WebhookWorker(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public JobType type() {
        return JobType.WEBHOOK;
    }

    @Override
    public Mono<Void> process(Job job) {
        return Mono.fromCallable(() -> objectMapper.readTree(job.getPayload().asString()))
                .flatMap(payload -> {
                    String url = payload.path("url").asText(null);
                    if (url == null || url.isBlank()) {
                        return Mono.error(new IllegalArgumentException(
                                "WEBHOOK job requires a 'url' field in its payload"));
                    }
                    JsonNode body = payload.has("body") ? payload.get("body") : objectMapper.createObjectNode();
                    log.info("[WEBHOOK] POST {} for job {}", url, job.getId());
                    return webClient.post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(body)
                            .retrieve()
                            .toBodilessEntity()
                            .timeout(Duration.ofSeconds(10))
                            .doOnNext(resp -> log.info("[WEBHOOK] job {} -> {} responded {}",
                                    job.getId(), url, resp.getStatusCode()))
                            .then();
                });
    }
}
