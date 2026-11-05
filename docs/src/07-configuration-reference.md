---
title: Configuration Reference
subtitle: Every environment variable, default, owner and effect across the ProctorAI stack.
id: PAI-CFG-007
version: 1.0
category: Operations
audience: Operators and engineers
classification: Confidential
pdf: 07-ProctorAI-Configuration-Reference
related: Deployment Runbook (PAI-DEP-006), Security & Hardening (PAI-SEC-005)
---

## Configuration model

ProctorAI is configured entirely through environment variables. Setup scripts generate one env file per stack:

| Stack | File | Created by |
|---|---|---|
| Development | `backend/.env` | `./setup.sh` |
| Production from source | `backend/.env.production` | `./setup.sh prod` |
| Production from published images | `docker/.env` | `docker/setup.sh` |

All env files are created with mode `600`. `backend/.env.example` lists every variable with placeholders.

### Ownership legend

| Owner | Meaning |
|---|---|
| **Setup (once)** | Generated on first run and never changed automatically. Changing it later requires a coordinated rotation. |
| **Setup (every run)** | Rewritten on every setup run from what is actually served. Do not edit by hand. |
| **Prompt** | Asked by setup; rerun setup to change. |
| **You** | Safe to edit; rerun setup (`--yes`) to apply. |
| **Compose override †** | Read by the service, but not passed through by any compose file in 1.1.0. Setting it in the env file has no effect. To change it, add it to the service's `environment:` block in the compose file used by setup, then rerun setup. |

> [!IMPORTANT]
> After editing an env file, apply it with `./setup.sh` (development), `./setup.sh prod --yes` (source) or `./setup.sh` in `docker/` (published images). The production compose file fails fast when a required variable is missing.

## Database

| Variable | Default / generated | Owner | Description |
|---|---|---|---|
| `POSTGRES_DB` | `proctor` | Setup (once) | Database name |
| `POSTGRES_USER` | `proctor_admin` | Setup (once) | Database owner; used only by `db-migrator` |
| `POSTGRES_PASSWORD` | 32 random chars | Setup (once) | Owner password |
| `AUTHENTICATOR_USER_NAME` | `authenticator` | Setup (once) | Role used by auth-service |
| `AUTHENTICATOR_USER_PASSWORD` | 32 random chars | Setup (once) | Password of `authenticator` (set by the migrator) |
| `APP_USER_NAME` | `proctor` | Setup (once) | Role used by all other services |
| `APP_USER_PASSWORD` | 32 random chars | Setup (once) | Password of `proctor` (set by the migrator) |
| `ADMIN_PASSWORD` | `admin123` (dev) / 20 random chars (prod) | Setup (once) | Password of `admin@college.com` on a new database |
| `R2DBC_URL` | `r2dbc:postgresql://postgres:5432/<db>` in containers | Compose | Reactive connection URL |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/<db>` (face-service) | Compose | JDBC URL for Spring AI vector store |
| `MIGRATION_DATABASE_URL` | `jdbc:postgresql://postgres:5432/<db>` | Compose | JDBC URL for Flyway |
| `POSTGRES_PORT` | `5433` | You (dev) | Host port for PostgreSQL in development |

## Authentication and sessions

| Variable | Default | Owner | Description |
|---|---|---|---|
| `JWT_SECRET` | 64 random chars | Setup (once) | HMAC key for access tokens; must be ≥ 32 bytes |
| `JWT_ISSUER` | `proctor.local` | Setup (once) | `iss` claim |
| `JWT_ACCESS_EXPIRE_IN_SEC` | `900` | You | Access token lifetime (15 minutes) |
| `JWT_REFRESH_EXPIRE_IN_SEC` | `2592000` | You | Refresh token lifetime (30 days) |
| `APP_COOKIE_SECURE` | `false` (dev) / `true` (prod) | Setup (every run) | `Secure` flag on cookies |
| `APP_COOKIE_SAME_SITE` | `Lax` | Compose override † | `SameSite` attribute of cookies |
| `LOGIN_MAX_FAILURES` | `10` | You | Failed sign-ins before an account is locked |
| `LOGIN_LOCKOUT` | `15m` | You | Lockout duration (Spring duration format: `30s`, `15m`, `1h`) |

## Gateway, web and CORS

| Variable | Default | Owner | Description |
|---|---|---|---|
| `APP_ALLOWED_ORIGINS` | Generated | Setup (every run) | CORS origins: dev `http://localhost:<port>`, `http://127.0.0.1:<port>`; prod `https://<app domain>` (or `http://<ip>` before DNS) plus native app origins |
| `APP_EXTRA_ORIGINS` | empty | You | Extra allowed origins, comma separated. Validated: no wildcards, HTTP(S) only, storage and registry origins refused |
| `APP_TRUSTED_PROXIES` | `1` (dev) / `2` (prod) | Compose | Number of proxies that append to `X-Forwarded-For` (Caddy and Nginx in production) |
| `LOGIN_RATE_LIMIT_REPLENISH` | `1` | You | Tokens added per second to each client IP bucket |
| `LOGIN_RATE_LIMIT_BURST` | `30` | You | Bucket capacity |
| `LOGIN_RATE_LIMIT_COST` | `3` | You | Tokens per sign-in attempt (defaults: 10 immediate attempts, then 20/min) |
| `GATEWAY_SERVER_PORT` | `7050` | You (dev) | Host port of the gateway in development |
| `FRONTEND_PORT` | `5173` | You (dev) | Host port of the web app in development |

## Domains and TLS (production)

| Variable | Example | Owner | Description |
|---|---|---|---|
| `APP_DOMAIN` | `app.example.com` | Prompt | Web app and API |
| `FILES_DOMAIN` | `files.example.com` | Prompt | Object storage for presigned URLs |
| `REGISTRY_DOMAIN` | `registry.example.com` | Prompt | Eureka dashboard |
| `ACME_EMAIL` | `ops@example.com` | Prompt | Let's Encrypt account email |
| `PUBLIC_IP` | detected | Prompt / auto | Server public IPv4, used before DNS is ready |

## Service registry

| Variable | Default | Owner | Description |
|---|---|---|---|
| `DISCOVERY_USERNAME` | `proctor-discovery` | Setup (once) | Basic auth user for Eureka |
| `DISCOVERY_PASSWORD` | 48 random chars | Setup (once) | Basic auth password (≥ 32 chars, enforced) |
| `DISCOVERY_SERVER_PORT` | `8761` | You | Host port (bound to `127.0.0.1` in production) |
| `DISCOVERY_SERVER_REFRESH_INTERVAL` | `2` | You (dev) | Registry refresh interval in seconds |
| `DISCOVERY_DASHBOARD_ENABLED` | `true` in prod compose | Compose | Serves the dashboard behind the registry domain |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Built from the credentials | Compose | Registry URL used by every service |

## Redis

| Variable | Default | Owner | Description |
|---|---|---|---|
| `REDIS_HOST` | `redis` in containers | Compose | Host |
| `REDIS_PORT` | `6379` in containers / `6390` dev host port | Compose / You | Port |
| `REDIS_PASSWORD` | 32 random chars | Setup (once) | `requirepass` password |

## Object storage (MinIO / S3)

| Variable | Default | Owner | Description |
|---|---|---|---|
| `MINIO_ROOT_USER` | `proctorminio` | Setup (once) | MinIO root user (console and init only) |
| `MINIO_ROOT_PASSWORD` | 32 random chars | Setup (once) | MinIO root password |
| `S3_ACCESS_KEY` | `proctor-app` | Setup (once) | Application user, limited to one bucket |
| `S3_SECRET_KEY` | 40 random chars | Setup (once) | Application user secret |
| `S3_BUCKET` | `proctor-files` | Setup (once) | Bucket for complaint files |
| `S3_ENDPOINT` | `http://minio:9000` in containers | Compose | Internal S3 endpoint |
| `S3_PUBLIC_ENDPOINT` | `http://localhost:9200` (dev) / `https://<files domain>` | Setup (every run) | Base URL in presigned links; must be reachable by clients |
| `MINIO_CORS_ALLOW_ORIGIN` | App origin | Setup (every run) | Origins allowed to upload directly to MinIO |
| `S3_PORT` | `9200` | You | Host port for the S3 API (dev; prod only before DNS) |
| `S3_CONSOLE_PORT` | `9201` | You | Host port of the MinIO console (bound to `127.0.0.1`) |

## Temporary files

| Variable | Default | Owner | Description |
|---|---|---|---|
| `APP_STORAGE_DIR` | `./storage` (dev) / `/data` (containers) | Compose | Directory for import job files (user-service, face-service) |
| `APP_STORAGE_TEMP_TTL` | `30m` | Compose override † | Maximum age of temporary job files |
| `APP_STORAGE_SWEEP_INTERVAL` | `15m` | Compose override † | How often the sweeper runs |

## Notifications

| Variable | Default | Owner | Description |
|---|---|---|---|
| `NOTIFICATION_SECRET_KEY` | 48 random chars | Setup (once) | Encrypts FCM service-account secrets (AES-256-GCM). Rotating it requires re-entering FCM credentials |

FCM credentials themselves are entered in the application (Admin → Notifications), not in env files.

## Face recognition

| Variable | Default | Owner | Description |
|---|---|---|---|
| `FACE_SERVER_PORT` | `7059` | Compose | HTTP port |
| `FACE_DETECTION_MODEL_PATH` | `./models/face-detector.onnx` (image: `/app/models/…`) | Image | SCRFD model file |
| `FACE_EMBEDDING_MODEL_PATH` | `./models/face-embedder.onnx` | Image | ArcFace model file |
| `FACE_DETECTION_INPUT_SIZE` | `640` | You | Detector input square size |
| `FACE_DETECTION_SCORE_THRESHOLD` | `0.5` | You | Minimum detector confidence |
| `FACE_DETECTION_NMS_THRESHOLD` | `0.4` | You | Non-maximum suppression IoU |
| `FACE_MATCH_THRESHOLD` | `0.45` in generated env files | You | Minimum cosine similarity to report a match |
| `FACE_MAX_IMAGE_BYTES` | `8388608` | You | Maximum decoded image size (8 MB) |
| `FACE_EMBEDDING_DIMENSIONS` | `512` | Image | Must match the embedder output |
| `FACE_SELF_ENROLL_LIMIT` | `1` | Compose override † | Self-enrollment attempts before lock |

> [!NOTE]
> Generated env files set `FACE_MATCH_THRESHOLD=0.45`. The production compose file falls back to `0.75` only when the variable is absent. Higher values reduce false matches but increase missed matches. Validate the threshold on a sample of your own users before go-live.

## Versions and images

| Variable | Default | Stack | Description |
|---|---|---|---|
| `APP_VERSION` | `latest` | Source production | Tag for locally built images |
| `PROCTOR_VERSION` | `1.1.0` | Published images | Image version pulled from Docker Hub (`./setup.sh update <version>` sets it) |
| `IMAGE_REPOSITORY` | `vishaltyagi807/proctor-ai` | Published images | Docker Hub repository |

## Frontend and native apps

| Variable | Where | Description |
|---|---|---|
| `VITE_SERVER_URL` | Build time (`npm run build`, `tauri:*:build`) | Server URL baked into native apps; when empty, the app asks at sign-in |
| `APPLE_DEVELOPMENT_TEAM` | iOS builds | Apple team id for signing |
| `APPLE_SIGNING_IDENTITY`, `APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID` | macOS builds | Code signing and notarisation |
| `ANDROID_HOME`, `NDK_HOME`, `JAVA_HOME` | Android builds | Toolchain locations |

## JVM and container settings (compose)

| Setting | Development | Production |
|---|---|---|
| `JAVA_TOOL_OPTIONS` | C1 only, Serial GC, CDS archive (fast start) | `-XX:+UseG1GC -XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=25 -XX:+ExitOnOutOfMemoryError` |
| Memory limits | None | 768 MB per service, 2560 MB face-service, 512 MB registry and monitor, 384 MB migrator, 256 MB edge, 128 MB frontend |
| Health check | `/actuator/health` | Same, 15 s interval, 120 s start period |
| Logging | Docker default | `json-file`, 10 MB × 5 |

## Ports

| Port | Development (host) | Production (host) |
|---|---|---|
| 80 / 443 tcp, 443 udp | — | Caddy edge (public) |
| 5173 | Web app | — |
| 7050 | Gateway | — |
| 7051–7060 | Each service on `127.0.0.1` | — |
| 8761 | Registry | `127.0.0.1` only |
| 5433 | PostgreSQL | — |
| 6390 | Redis | — |
| 9200 | MinIO API | Public only before DNS |
| 9201 | MinIO console | `127.0.0.1` only |

## Change procedure

1. Back up the env file: `cp .env .env.bak-$(date +%F)` (keep permissions `600`).
2. Edit only variables owned by **You** or **Prompt**.
3. Apply with the setup command for the stack.
4. Check `./setup.sh status` and the affected service logs.
5. Record the change in the operations log.
