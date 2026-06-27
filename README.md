# PulseGate

A self-hosted, reactive background job processing engine — like BullMQ/Celery, built from scratch
on Spring WebFlux, Redis Streams, and PostgreSQL (R2DBC). Services push jobs, workers pick them up,
process them, retry with exponential backoff, dead-letter the permanently failed, and a dark
terminal dashboard shows it all in real time.

## Architecture

```
 POST /api/jobs ──► JobController ──► JobProducer ──┬─► Postgres (jobs table, status=PENDING)
                                                    └─► Redis Stream  XADD pulsegate:jobs

 Redis Stream ──► JobConsumer (XREADGROUP, BLOCK 2s) ──► WorkerRegistry ──► Worker.process()
   │  backpressure: flatMap(concurrency)                                       │
   │                                                                           ├─ success → status DONE, XACK
   │                                                                           └─ failure → RetryPolicy
   │                                                                                 ├─ attempts<max → FAILED + next_retry_at, XACK
   │                                                                                 └─ exhausted  → DEAD (DeadLetterHandler), XACK
   │
   ├─ RetryScheduler (every 5s)      re-enqueues FAILED jobs whose backoff elapsed
   └─ PendingJobReclaimer (every 60s) XPENDING → re-enqueue jobs from crashed workers
```

Everything is non-blocking end to end (Netty + R2DBC + Lettuce). The single concurrency number in
`JobConsumer` is the backpressure valve that caps how many jobs run at once.

## Tech stack

Java 21 · Spring Boot 3.3 (WebFlux) · Redis Streams (Lettuce) · PostgreSQL 16 (R2DBC) · Micrometer/
Prometheus · Docker Compose · Helm/Minikube · React 18 + Vite + Tailwind · GitHub Actions.

## Prerequisites

- Docker + Docker Compose (the simplest path — no local JDK/Maven/Node needed)
- For local dev without Docker: JDK 21, Maven 3.9+, Node 20+

> Note: the build host this was scaffolded on has **JDK 24** and **no Maven/Docker on PATH**, so the
> code was written but not compiled/run here. The Docker image and CI both build on **JDK 21**
> (`maven:3.9-eclipse-temurin-21` / `actions/setup-java@21`), so those paths are unaffected. If you
> compile locally on JDK 24 and Lombok complains, bump `<lombok.version>` in `pom.xml`.

## Quick start (Docker)

```bash
cp .env.example .env

# App + Postgres + Redis + dashboard
docker compose up --build

# ...or include Prometheus + Grafana (one combined project so Prometheus can scrape pulsegate)
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up --build
```

| Service     | URL                                   |
|-------------|---------------------------------------|
| API         | http://localhost:8080/api             |
| Actuator    | http://localhost:8080/actuator/health |
| Prometheus  | http://localhost:8080/actuator/prometheus |
| Dashboard   | http://localhost:5173                 |
| Prometheus  | http://localhost:9090                 |
| Grafana     | http://localhost:3000 (admin/admin)   |

## Local dev (without Docker)

```bash
# backend (needs Postgres + Redis reachable per .env)
mvn spring-boot:run

# frontend
cd dashboard && npm install && npm run dev
```

## REST API

| Method | Path                              | Purpose                          |
|--------|-----------------------------------|----------------------------------|
| POST   | `/api/jobs`                       | Submit a job                     |
| GET    | `/api/jobs/{id}`                  | Get job status                   |
| DELETE | `/api/jobs/{id}`                  | Cancel a PENDING job             |
| GET    | `/api/jobs?status=&type=&page=&size=` | List jobs (filtered, paginated) |
| GET    | `/api/stats`                      | Queue/worker stats snapshot      |
| GET    | `/api/stats/stream`               | SSE stats (every 2s)             |
| GET    | `/api/dead-letter`                | List dead jobs                   |
| POST   | `/api/dead-letter/{id}/retry`     | Manually retry a dead job        |

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"type":"EMAIL","payload":{"to":"test@test.com"},"priority":5}'
```

To watch **retries + dead-letter** in action, submit a `WEBHOOK` job with an unreachable URL:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"type":"WEBHOOK","payload":{"url":"http://127.0.0.1:1/nope"}}'
# fails → FAILED (retry in 5s) → FAILED (retry in 25s) → DEAD (with MAX_RETRY_ATTEMPTS=3)
```

## Verification commands

```bash
# Redis stream
docker exec -it pulsegate-redis-1 redis-cli XLEN pulsegate:jobs
docker exec -it pulsegate-redis-1 redis-cli XPENDING pulsegate:jobs pulsegate-workers - + 10

# Postgres
docker exec -it pulsegate-postgres-1 psql -U pulsegate_user -d pulsegate \
  -c "SELECT id, type, status, attempts FROM jobs ORDER BY created_at DESC LIMIT 10;"

# Metrics (all five custom metrics)
curl -s http://localhost:8080/actuator/prometheus | grep pulsegate

# Stats
curl -s http://localhost:8080/api/stats
```

Exposed metrics: `pulsegate_queue_depth`, `pulsegate_jobs_processed_total`,
`pulsegate_job_processing_seconds`, `pulsegate_worker_active_count`, `pulsegate_dead_letter_total`.

## Kubernetes (Helm)

```bash
helm install pulsegate ./helm/pulsegate
# HPA scales on the custom metric pulsegate_queue_depth (needs prometheus-adapter installed)
kubectl get hpa
```

## Configuration

All via environment variables (see `.env.example`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`REDIS_URL`, `WORKER_CONCURRENCY`, `POLL_INTERVAL_MS`, `MAX_RETRY_ATTEMPTS`. No secrets committed.
