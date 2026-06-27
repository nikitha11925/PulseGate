package com.pulsegate.api;

import com.pulsegate.api.dto.JobResponse;
import com.pulsegate.api.dto.JobSubmitRequest;
import com.pulsegate.model.Job;
import com.pulsegate.model.JobStatus;
import com.pulsegate.model.JobType;
import com.pulsegate.queue.InFlightJobs;
import com.pulsegate.queue.JobProducer;
import com.pulsegate.repository.JobRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Reactive REST API for jobs. Every handler returns {@code Mono}/{@code Flux} — no blocking,
 * no servlet types (this is WebFlux, not MVC).
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobProducer jobProducer;
    private final JobRepository jobRepository;
    private final JobMapper jobMapper;
    private final InFlightJobs inFlightJobs;

    /** Submit a job. Returns 202 with the persisted job (id assigned) immediately. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<JobResponse> submit(@Valid @RequestBody JobSubmitRequest request) {
        return jobProducer.submit(request.getType(), request.getPayload(), request.getPriority())
                .map(jobMapper::toResponse);
    }

    /** Get a single job's status. */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<JobResponse>> get(@PathVariable UUID id) {
        return jobRepository.findById(id)
                .map(jobMapper::toResponse)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Cancel a job. PENDING jobs are marked CANCELLED so the consumer skips them when the queued
     * stream message is read. PROCESSING jobs are interrupted in-flight via {@link InFlightJobs}
     * (the running worker is disposed and the consumer marks it CANCELLED). Finished jobs (DONE /
     * FAILED / DEAD / already CANCELLED) can't be cancelled — 409.
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Object>> cancel(@PathVariable UUID id) {
        return jobRepository.findById(id)
                .flatMap(job -> switch (job.getStatus()) {
                    case PENDING -> markCancelled(job, id, "cancelled");
                    case PROCESSING -> inFlightJobs.requestCancel(id)
                            // It's running here: the consumer will flip it to CANCELLED once the
                            // worker Mono is disposed. Report "cancelling".
                            ? Mono.just(ok(id, "cancelling"))
                            // Not in flight on this instance (e.g. a stuck job) — mark it directly.
                            : markCancelled(job, id, "cancelled");
                    default -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                            .body((Object) Map.of(
                                    "error", "Cannot cancel a job in status " + job.getStatus().name(),
                                    "status", job.getStatus().name())));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body((Object) Map.of("error", "Job not found", "id", id.toString())));
    }

    private Mono<ResponseEntity<Object>> markCancelled(Job job, UUID id, String state) {
        job.setStatus(JobStatus.CANCELLED);
        job.setUpdatedAt(LocalDateTime.now());
        return jobRepository.save(job).thenReturn(ok(id, state));
    }

    private static ResponseEntity<Object> ok(UUID id, String state) {
        return ResponseEntity.ok((Object) Map.of("status", state, "id", id.toString()));
    }

    /** List jobs, optionally filtered by status and/or type, paginated. */
    @GetMapping
    public Flux<JobResponse> list(@RequestParam(required = false) JobStatus status,
                                  @RequestParam(required = false) JobType type,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        int limit = Math.max(1, Math.min(size, 200));
        int offset = Math.max(0, page) * limit;
        Flux<Job> jobs;
        if (status != null && type != null) {
            jobs = jobRepository.findPageByStatusAndType(status.name(), type.name(), limit, offset);
        } else if (status != null) {
            jobs = jobRepository.findPageByStatus(status.name(), limit, offset);
        } else if (type != null) {
            jobs = jobRepository.findPageByType(type.name(), limit, offset);
        } else {
            jobs = jobRepository.findPage(limit, offset);
        }
        return jobs.map(jobMapper::toResponse);
    }
}
