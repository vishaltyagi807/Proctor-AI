<div align="center">

# ⚙️ ProctorAI Backend

A set of reactive Spring Boot microservices behind a Spring Cloud Gateway, with authorization enforced by PostgreSQL row-level security.

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2025.0-6DB33F?style=flat-square&logo=spring&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.0-02303A?style=flat-square&logo=gradle&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16_+_pgvector-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.4-DC382D?style=flat-square&logo=redis&logoColor=white)
![MinIO](https://img.shields.io/badge/MinIO-S3-C72E49?style=flat-square&logo=minio&logoColor=white)

</div>

---

## 🚀 Running

The backend runs only through Docker Compose. Start it from the repository root:

```bash
./setup.sh            # development stack
./setup.sh prod       # production stack
```

The script generates `.env` (or `.env.production`) with random secrets, downloads the face models, and builds and starts every container. You can also call Compose directly from this folder:

```bash
docker compose up -d --build                                                    # development
docker compose -f docker-compose.prod.yaml --env-file .env.production up -d --build   # production
```

> 💡 After you change backend code, run `./setup.sh` again to rebuild the affected images.

---

## 🧱 Services

| Module | Port | Gateway path | Responsibility |
|---|:---:|---|---|
| `discovery-server` | 8761 | — | Eureka service registry, protected by HTTP Basic authentication |
| `api-gateway` | 7050 | — | Routing, CORS, and load balancing over `lb://` |
| `auth-service` | 7051 | `/auth/**` | Login, refresh, logout, and logout from all sessions |
| `user-service` | 7052 | `/users/**` | Users, profiles, and bulk CSV import |
| `department-service` | 7053 | `/departments/**` | Departments and members |
| `role-service` | 7054 | `/roles/**` | Roles and the permission matrix |
| `complaint-service` | 7055 | `/complaints/**` | Complaints, comments, assignment, status, and history |
| `storage-service` | 7056 | `/complaints/*/files/**` | Presigned S3 uploads and downloads |
| `custom-field-service` | 7057 | `/custom-fields/**` | Custom field definitions |
| `notification-service` | 7058 | `/notifications/**` | Real-time notifications, preferences, devices, and FCM |
| `face-service` | 7059 | `/faces/**` | Face detection, enrollment, and recognition ([details](face-service/README.md)) |
| `monitor-service` | 7060 | `/system/**` | Health of service instances and overview stats |

### Shared libraries

| Module | Provides |
|---|---|
| `common-lib` | Shared DTOs, enums, and exceptions |
| `common-security` | WebFlux security, JWT handling, password hashing, and `CurrentUser` |
| `common-persistence` | R2DBC setup, RLS transactions, search helpers, and SQL error mapping |
| `common-storage` | `FileStorage` for temporary task files, plus a background sweeper that removes leftover files |
| `common-customfields` | Reading, validating, and storing custom field values |
| `common-notifications` | `NotificationPublisher` over Redis, plus progress streams |
| `common-monitoring` | Instance sampling and reporting for the system monitor |

---

## 🔐 Security Model

```mermaid
sequenceDiagram
    participant C as Client
    participant G as API Gateway
    participant S as Service
    participant DB as PostgreSQL

    C->>G: Request + JWT
    G->>S: Forward (lb://SERVICE)
    S->>S: Validate JWT and build UserPrincipal
    S->>DB: begin; set_config('auth.uid', ...)
    S->>DB: Query
    DB->>DB: RLS policy calls can_access(entity, action, row)
    DB-->>S: Rows the user may see
    S-->>C: Response
```

- **Tokens.** Login returns a short-lived JWT access token (15 minutes by default). The refresh token (30 days by default) is kept in an HTTP-only cookie.
- **Row-level security.** Each request runs in a transaction that sets `auth.uid` and `auth.email`. The RLS policies in `init/05_rls_policies.sql` use these values to filter every read and write.
- **Permissions.** A permission is a combination of entity, action (`read`, `write`, `update`, `delete`), and scope (`own`, `department`, `all`). Roles bundle permissions, and users get roles.
- **Database roles.** Services connect through the low-privilege `authenticator` and `proctor` roles. They never use the database owner.
- **Service registry.** Every Eureka call and the dashboard require `DISCOVERY_USERNAME` and `DISCOVERY_PASSWORD` (at least 32 characters). The server refuses to start without them, and only `/actuator/health` is public. Credentials are compared in constant time, and the dashboard is turned off in production.

---

## 🗄️ Database

`init/` holds the complete schema. Postgres applies it automatically the **first time** the `postgres_data` volume is created.

| File | Contents |
|---|---|
| `01_create_roles.sh` | Database roles |
| `02_extensions.sql` | `pgcrypto`, `vector`, `hstore`, `uuid-ossp` |
| `03_schema.sql` | Tables, enums, and indexes |
| `04_security_functions.sql` | `can_access`, `has_scope`, and related helpers |
| `05_rls_policies.sql` | Row-level security policies |
| `06_triggers.sql` | Triggers |
| `06b_storage_functions.sql` | Storage functions |
| `06c_notification_functions.sql` | Notification functions |
| `06d_monitor_functions.sql` | Monitor functions |
| `07_seed.sql` | Permissions, default roles, and the admin user |
| `08_grants.sql` | Privileges for the roles |

> ⚠️ **All schema and seed changes go in `init/`.** The project has no migration files. To apply your changes locally, run `./setup.sh purge`, then `./setup.sh`. Purging removes every container and volume, so it **deletes all data**.

---

## 🐳 Docker

A single multi-stage `Dockerfile` builds every service:

| Stage | Purpose |
|---|---|
| `build` | Runs `./gradlew bootJar` once for all modules, with a cached Gradle home |
| `jre-musl` / `jre-glibc` | Builds a trimmed Java 21 runtime with `jlink`, keeping only the modules the services use, plus a CDS archive for faster startup |
| `extract` | Unpacks the selected `SERVICE` jar and removes ONNX Runtime native libraries for other platforms |
| `service` | `alpine` with the trimmed runtime, running as the non-root `app` user |
| `face-service` | Distroless `cc` image (glibc, needed by ONNX Runtime) with the trimmed runtime, a static `wget` for health checks, and the models. It has no shell |

| | Development (`docker-compose.yaml`) | Production (`docker-compose.prod.yaml`) |
|---|---|---|
| JVM | Fast startup (C1 only, Serial GC, CDS archive) | G1 GC with heap sized as a percentage of RAM |
| Exposed ports | Every service on `127.0.0.1` | Only the web frontend, on `HTTP_PORT` (default 80) |
| Hardening | — | Read-only filesystem, all capabilities dropped, `no-new-privileges`, memory limits |
| Logging | Default | JSON files with rotation (10 MB × 5) |
| Configuration | `.env` | `.env.production`, where required variables fail fast |

### Infrastructure ports (development)

| Container | Host port |
|---|---|
| PostgreSQL | `5433` |
| Redis | `6390` |
| MinIO API | `9200` |
| MinIO console | `9201` |

---

## ⚙️ Configuration

Every variable is listed in [`.env.example`](.env.example).

| Group | Variables |
|---|---|
| Database | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `AUTHENTICATOR_USER_*`, `APP_USER_*` |
| Auth | `JWT_SECRET`, `JWT_ISSUER`, `JWT_ACCESS_EXPIRE_IN_SEC`, `JWT_REFRESH_EXPIRE_IN_SEC`, `APP_COOKIE_SECURE` |
| Web | `APP_ALLOWED_ORIGINS` |
| Redis | `REDIS_PASSWORD` |
| Storage | `MINIO_ROOT_*`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`, `S3_PUBLIC_ENDPOINT` |
| Temporary files | `APP_STORAGE_TEMP_TTL` (default `30m`), `APP_STORAGE_SWEEP_INTERVAL` (default `15m`) |
| Notifications | `NOTIFICATION_SECRET_KEY` |
| Face recognition | `FACE_MATCH_THRESHOLD`, `FACE_DETECTION_SCORE_THRESHOLD`, `FACE_DETECTION_NMS_THRESHOLD`, `FACE_DETECTION_INPUT_SIZE`, `FACE_MAX_IMAGE_BYTES` |

---

## 🧪 Tools

- 📮 **Postman:** import [`postman/ProctorAI.postman_collection.json`](postman/ProctorAI.postman_collection.json) together with the development or production environment file from the same folder.
- 👥 **Sample data:** [`seed-data/users-bulk-import.csv`](seed-data/users-bulk-import.csv) is a ready-made file for the bulk user import. Its columns are `name, email, password, roles, departments, enabled, verified`.
- 🩺 **Health checks:** every service exposes `/actuator/health`, and Compose uses it to start containers in dependency order.

---

## 📁 Layout

```
backend/
├── build.gradle.kts            # shared Gradle config (Java 21, Spring BOMs, Lombok, MapStruct)
├── settings.gradle.kts         # module list
├── Dockerfile                  # multi-stage build for every service
├── docker-compose.yaml         # development stack
├── docker-compose.prod.yaml    # hardened production stack
├── init/                       # schema, RLS, functions, seed
├── common-*/                   # shared libraries
├── *-service/                  # microservices
├── api-gateway/
├── discovery-server/
├── postman/
└── seed-data/
```
