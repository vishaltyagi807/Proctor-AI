---
title: Architecture Document
subtitle: System context, containers, components, data flows and key design decisions.
id: PAI-ARC-002
version: 1.0
category: Architecture
audience: Engineers, architects, reviewers and operators
pdf: 02-ProctorAI-Architecture-Document
related: SRS (PAI-SRS-001), API Handbook (PAI-API-003), Database & Migrations (PAI-DB-004), Security & Hardening (PAI-SEC-005)
---

## Overview

ProctorAI is a reactive microservice system. A Spring Cloud Gateway routes every API call to one of ten domain services that are registered in a Eureka service registry. All services share one PostgreSQL 16 database (with pgvector), where authorization is enforced by Row-Level Security (RLS). Redis carries rate-limit buckets, notification streams, real-time fan-out, job progress and monitoring samples. MinIO stores uploaded files. A Caddy edge proxy terminates TLS in production.

This document follows the C4 model: system context, then containers, then components. It ends with cross-cutting concerns and recorded architecture decisions.

## System context

```mermaid
flowchart TB
  student([Student / member])
  staff([Staff / teacher])
  admin([Administrator])
  ops([Operator])
  integ([External system])
  pai[ProctorAI platform]
  le[[Let's Encrypt / ZeroSSL]]
  fcm[[Firebase Cloud Messaging]]
  hub[[Docker Hub]]
  student -->|files complaints, enrolls face| pai
  staff -->|processes complaints, recognises faces| pai
  admin -->|manages users, roles, settings| pai
  integ -->|REST API| pai
  ops -->|deploys, monitors, backs up| pai
  pai -->|TLS certificates| le
  pai -.->|optional push| fcm
  ops -->|pulls images| hub
```

## Container view

```mermaid
flowchart TB
  caddy["Caddy edge proxy<br/>TCP 80 · TCP/UDP 443"]
  nginx["frontend<br/>Nginx + React SPA"]
  gw["api-gateway :7050"]
  disc["discovery-server :8761<br/>Eureka registry"]
  svc["Domain services :7051–7060<br/>auth · user · department · role · complaint<br/>storage · custom-field · notification · face · monitor"]
  mig["db-migrator<br/>Flyway, runs once per start"]
  pg[("PostgreSQL 16<br/>pgvector · RLS")]
  rd[("Redis 7.4")]
  s3[("MinIO")]
  caddy -->|app domain| nginx
  caddy -->|files domain| s3
  caddy -->|registry domain| disc
  nginx -->|/api/*| gw
  gw -->|lb://| svc
  gw -.->|lookup| disc
  svc -.->|register| disc
  svc --> pg
  svc --> rd
  gw --> rd
  svc -->|storage-service| s3
  mig --> pg
```

### Containers and responsibilities

| Container | Technology | Responsibility | Gateway path |
|---|---|---|---|
| `edge` | Caddy 2.11 | TLS (Let's Encrypt, ZeroSSL fallback), HTTP/3, HSTS, security headers, routing by domain | — |
| `frontend` | Nginx + React 19 SPA | Serves the web app, proxies `/api/` to the gateway, blocks `/api/actuator/` | — |
| `api-gateway` | Spring Cloud Gateway | Routing via `lb://`, CORS, login rate limit, client IP resolution | — |
| `discovery-server` | Netflix Eureka | Service registry protected by HTTP Basic auth | — |
| `auth-service` | WebFlux + R2DBC | Login, refresh rotation, logout, lockout | `/auth/**` |
| `user-service` | WebFlux + R2DBC | Users, profile, bulk import, user event stream | `/users/**` |
| `department-service` | WebFlux + R2DBC | Departments and members | `/departments/**` |
| `role-service` | WebFlux + R2DBC | Roles, permission catalogue and matrix | `/roles/**` |
| `complaint-service` | WebFlux + R2DBC | Complaints, comments, assignment, status, history, dashboard stats | `/complaints/**` |
| `storage-service` | WebFlux + AWS SDK | Presigned uploads/downloads, file confirmation, stale upload purge | `/complaints/*/files/**` |
| `custom-field-service` | WebFlux + R2DBC | Custom field definitions | `/custom-fields/**` |
| `notification-service` | WebFlux + Redis | Inbox, SSE stream, preferences, devices, FCM, deliveries | `/notifications/**` |
| `face-service` | WebFlux + ONNX Runtime + Spring AI | Detection, embedding, enrollment, recognition, audit | `/faces/**` |
| `monitor-service` | WebFlux + OSHI | Host, service, DB, Redis and storage metrics | `/system/**` |
| `db-migrator` | Spring Boot + Flyway | Applies migrations, sets role passwords, then exits | — |
| `postgres` | PostgreSQL 16 + pgvector | System of record, RLS, vector index | — |
| `redis` | Redis 7.4 (AOF on) | Rate limits, streams, pub/sub, progress, monitoring samples | — |
| `minio` / `minio-init` | MinIO | Object storage; init creates bucket, policy and app user | — |

## Component view

### Shared libraries

Every domain service is assembled from the same building blocks:

| Library | Key components |
|---|---|
| `common-lib` | DTOs (`UserDTO`, `PageResponse`, `ApiError`…), enums (`EntityType`, `PermissionScope`…), typed exceptions mapped to HTTP status |
| `common-security` | `JwtAuthenticationWebFilter`, `JwtTokenService` (HMAC, secret ≥ 32 bytes), `TokenExtractor` (cookie `token` or `Authorization: Bearer`), `CurrentUser`, `BcryptPasswordHasher`, `GlobalExceptionHandler` |
| `common-persistence` | `SqlGateway` over R2DBC, `RlsTransaction` / `SecureTransaction`, `SearchQueryParser` (filter DSL), `PagedQueryRunner`, `PersistenceExceptionHandler` (SQLSTATE → HTTP), `DatabaseRoleGuard` |
| `common-storage` | `FileStorage` for temporary job files and `TemporaryFileSweeper` |
| `common-customfields` | Definition reader, validator and value store used by services that own extendable entities |
| `common-notifications` | `NotificationPublisher` (Redis stream), `ProgressBroker` (job progress) |
| `common-monitoring` | `InstanceSampler` / `InstanceReporter` publishing JVM and request metrics to Redis every 2 s |

### Layering inside a service

```mermaid
flowchart LR
  c[Controller<br/>validation · DTOs] --> s[Service<br/>business rules]
  s --> r[Repository<br/>SQL via SqlGateway]
  r --> t[SecureTransaction<br/>set auth.uid, auth.email]
  t --> db[(PostgreSQL<br/>RLS + triggers)]
  s --> n[NotificationPublisher]
  n --> redis[(Redis stream)]
```

Controllers are thin. Services hold the workflow. Repositories issue explicit SQL through `SqlGateway`. Every request-scoped query runs inside a transaction that first sets `auth.uid` and `auth.email`, so RLS policies and triggers see the caller.

## Key flows

### Request and authorization flow

```mermaid
sequenceDiagram
  participant B as Browser / App
  participant E as Caddy
  participant N as Nginx
  participant G as Gateway
  participant S as Service
  participant P as PostgreSQL
  B->>E: HTTPS /api/complaints?status=eq.pending
  E->>N: proxy
  N->>G: /complaints?status=eq.pending
  G->>G: CORS, resolve client IP (trusted proxies)
  G->>S: lb://COMPLAINT-SERVICE
  S->>S: Validate JWT, build UserPrincipal
  S->>P: BEGIN, then set_config auth.uid
  S->>P: SELECT … FROM complaints WHERE …
  P->>P: RLS policy → can_access('complaints','read', row)
  P-->>S: only rows the caller may see
  S-->>B: 200 PageResponse<ComplaintDTO>
```

### Sign-in and token rotation

```mermaid
sequenceDiagram
  participant C as Client
  participant G as Gateway
  participant A as auth-service
  participant P as PostgreSQL
  C->>G: POST /auth/login {email, password}
  G->>G: Redis token bucket per client IP
  G->>A: forward (X-Client-IP)
  A->>A: Lockout check (failures in window)
  A->>P: verify bcrypt hash, issue refresh token (hash stored, new family)
  A-->>C: 200 user + token info, Set-Cookie token, refresh_token
  Note over C,A: 15 minutes later
  C->>A: POST /auth/refresh (cookie or body)
  A->>P: token valid and not revoked → revoke it, issue next in same family
  A-->>C: new access + refresh token
  C->>A: POST /auth/refresh with an old token
  A->>P: token already revoked → revoke whole family
  A-->>C: 401
```

### Notification flow

```mermaid
flowchart LR
  svc[complaint-service<br/>or any publisher] -->|XADD notifications:inbound| stream[(Redis stream)]
  stream --> ns[notification-service consumer]
  ns -->|insert + recipients| pg[(PostgreSQL)]
  ns -->|PUBLISH notifications:user:&lt;id&gt;| ch[(Redis pub/sub)]
  ch --> sse[SSE /notifications/stream]
  sse --> client[Web / desktop / mobile]
  ns -->|if push enabled and device registered| fcm[[FCM]]
  ns -->|log| del[(notification_deliveries)]
```

Recipients are resolved in the database (`resolve_notification_recipients`) from user, role and department targets. Preferences (`push_enabled`, `realtime_enabled`, `muted_types`) are applied per user before delivery. Every channel attempt is logged as `sent`, `failed` or `skipped`.

### File upload flow

```mermaid
sequenceDiagram
  participant C as Client
  participant S as storage-service
  participant P as PostgreSQL
  participant M as MinIO (files domain)
  C->>S: POST /complaints/{id}/files/upload-url {fileName, contentType, sizeBytes}
  S->>P: insert complaint_files (status pending) — RLS checks write access
  S-->>C: {fileId, uploadUrl, method: PUT, headers, expiresAt}
  C->>M: PUT uploadUrl (direct, presigned)
  C->>S: POST /complaints/{id}/files/{fileId}/confirm
  S->>M: HEAD object (verify)
  S->>P: status → uploaded
  C->>S: GET …/{fileId}/download-url
  S-->>C: presigned GET URL (time-limited)
```

Unconfirmed uploads are purged by `purge_stale_uploads`. File bytes never pass through the API services.

### Face recognition flow

```mermaid
flowchart LR
  img[Base64 image] --> dec[Decode · size check ≤ 8 MB]
  dec --> det[SCRFD-10G detector<br/>640×640, NMS 0.4]
  det --> align[5-point alignment<br/>112×112]
  align --> emb[ArcFace R50<br/>512-d L2-normalised]
  emb --> vs[(pgvector<br/>face_embeddings)]
  vs -->|cosine ≥ threshold| scope{caller may read user?}
  scope -->|yes| match[matched + user]
  scope -->|no| hide[reported as not matched]
  match & hide --> draw[Annotated PNG + JSON]
  draw --> audit[(face_audit_log)]
```

### System monitor flow

Each service runs `InstanceReporter`, which writes a JVM and request sample to Redis key `monitor:instance:<id>` every 2 seconds with a 10-second TTL. `monitor-service` combines those samples with the Eureka registry, host metrics (CPU, memory, disks, network), PostgreSQL statistics functions, Redis `INFO` and storage usage into a `SystemSnapshot`, keeps a short history, derives issues, and streams it over SSE at `/system/stream`.

## Deployment view

```mermaid
flowchart TB
  internet((Internet)) -->|80, 443 tcp/udp| caddy
  internet -.->|9200 before DNS only| caddy
  subgraph host[Linux host · Docker]
    caddy[edge]
    subgraph net[Compose network proctor-prod]
      frontend --> gateway[api-gateway]
      gateway --> services[10 domain services]
      services --> postgres[(postgres)]
      services --> redis[(redis)]
      services --> minio[(minio)]
      registry[discovery-server]
    end
    caddy --> frontend
    caddy --> minio
    caddy --> registry
    vols[(Volumes: postgres_data · redis_data · minio_data<br/>user_storage · face_storage · monitor_probe<br/>caddy_data · caddy_config)]
  end
  admin([Operator SSH tunnel]) -.->|127.0.0.1:8761, :9201| host
```

| Domain | Served by | Purpose |
|---|---|---|
| `app.<domain>` | Caddy → frontend → gateway | Web app and API (`/api`) |
| `files.<domain>` | Caddy → MinIO | Presigned uploads and downloads |
| `registry.<domain>` | Caddy → discovery-server (dashboard only) | Eureka dashboard, behind Basic auth |

### Resource budget (production defaults)

| Container | Memory limit |
|---|---|
| Each domain service and gateway | 768 MB |
| `face-service` | 2560 MB |
| `discovery-server`, `monitor-service` | 512 MB |
| `db-migrator` | 384 MB |
| `edge` | 256 MB |
| `frontend` | 128 MB |

The JVM uses G1 with `MaxRAMPercentage=60` and exits on out-of-memory so Docker restarts it cleanly.

## Cross-cutting concerns

| Concern | Approach |
|---|---|
| Authentication | Stateless JWT in an `HttpOnly` cookie or bearer header, validated in every service |
| Authorization | RLS policies and `can_access` / `has_scope` functions in PostgreSQL; triggers protect invariants |
| Validation | Bean Validation on DTOs; database CHECK constraints and triggers as the last line |
| Errors | Uniform `ApiError {message, statusCode, status, timestamp}`; SQLSTATE mapped to HTTP |
| Configuration | Environment variables from the env file; production compose fails fast on missing values |
| Observability | Actuator health and Prometheus metrics; JSON logs with trace IDs; live system monitor |
| Resilience | Health-checked startup order, `restart: unless-stopped`, load-balancer cache TTL 5 s, Eureka lease 10 s |
| Real time | SSE streams fed by Redis pub/sub |
| Extensibility | Custom fields stored as typed JSON values, filterable via `cf.<key>` |
| Images | One multi-stage Dockerfile, jlink-trimmed JRE, CDS archive, non-root user, distroless face image |

## Scalability

The reference deployment runs on one host. The architecture supports growth in stages:

1. **Vertical:** increase host RAM/CPU and raise memory limits; face-service benefits most from CPU.
2. **Horizontal services:** run more replicas of a stateless service. The gateway load-balances through Eureka (`lb://`). SSE fan-out works across replicas through Redis pub/sub.
3. **Data tier:** move PostgreSQL to a managed or replicated instance with pgvector; move Redis to a managed instance; replace MinIO with any S3-compatible service by changing `S3_*` variables.
4. **Edge:** put a load balancer in front of several hosts and keep sticky-free routing (tokens are stateless).

## Architecture decision records

| ADR | Decision | Rationale | Consequence |
|---|---|---|---|
| ADR-01 | Reactive stack (WebFlux, R2DBC) | High concurrency with few threads, natural fit for SSE | Requires reactive-aware code and libraries |
| ADR-02 | Authorization in the database via RLS | A bug in service code cannot leak rows; one source of truth | Logic lives in SQL; needs dedicated RLS tests |
| ADR-03 | Single shared database | Simple operations, transactional integrity across domains | Services are coupled at the schema level; migrations are central |
| ADR-04 | Flyway migrations in a one-shot migrator | Safe automatic upgrades before services start | Released migrations are immutable |
| ADR-05 | On-premises face recognition (ONNX) | Privacy, no external AI cost, offline operation | Larger image and memory; model licence review |
| ADR-06 | Presigned S3 URLs | File bytes bypass services; scalable | Files domain must be reachable by clients; CORS on MinIO |
| ADR-07 | Redis for streams, pub/sub and rate limits | One lightweight dependency for several needs | Redis must be persistent (AOF on) and backed up |
| ADR-08 | Caddy edge with automatic TLS | Zero-touch certificates, HTTP/3 | Ports 80/443 must be reachable for ACME |
| ADR-09 | Tauri for native apps | One codebase, small native binaries | Platform toolchains needed per OS |
