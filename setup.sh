#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BACKEND_DIR="$ROOT_DIR/backend"
DEV_ENV="$BACKEND_DIR/.env"
PROD_ENV="$BACKEND_DIR/.env.production"
MODELS_DIR="$BACKEND_DIR/face-service/models"
MODELS_URL="https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip"
MODELS_MIRROR="https://sourceforge.net/projects/insightface.mirror/files/v0.7/buffalo_l.zip/download"
SEED_FILE="$BACKEND_DIR/init/07_seed.sql"

if [ -t 1 ]; then
    BOLD=$(printf '\033[1m')
    GREEN=$(printf '\033[32m')
    YELLOW=$(printf '\033[33m')
    RED=$(printf '\033[31m')
    RESET=$(printf '\033[0m')
else
    BOLD=""
    GREEN=""
    YELLOW=""
    RED=""
    RESET=""
fi

step() { printf '\n%s==> %s%s\n' "$BOLD" "$1" "$RESET"; }
ok() { printf '%s[ok]%s %s\n' "$GREEN" "$RESET" "$1"; }
warn() { printf '%s[warn]%s %s\n' "$YELLOW" "$RESET" "$1" >&2; }
fail() { printf '%s[error] %s%s\n' "$RED" "$1" "$RESET" >&2; exit 1; }

usage() {
    cat <<EOF
Usage: ./setup.sh [command] [--prod] [service]

Sets up and runs the complete ProctorAI system (backend + frontend) with Docker.
Only Docker is required on this machine.

Commands:
  dev       Set up and start the development stack (default)
  prod      Set up and start the production stack
  stop      Stop the containers and keep all data
  down      Remove the containers and keep all data
  status    Show every container and its health
  logs      Follow logs, for example: ./setup.sh logs api-gateway
  help      Show this message

Add --prod to stop, down, status or logs to act on the production stack.

Environment overrides:
  FRONTEND_PORT, GATEWAY_SERVER_PORT, DISCOVERY_SERVER_PORT   development host ports
  HTTP_PORT                                                    production host port
EOF
}

detect_os() {
    case "$(uname -s 2>/dev/null || echo unknown)" in
        Linux*)
            OS=linux
            if grep -qi microsoft /proc/version 2>/dev/null; then
                OS=wsl
            fi
            ;;
        Darwin*) OS=macos ;;
        MINGW* | MSYS* | CYGWIN*) OS=windows ;;
        *) OS=unknown ;;
    esac
    case "$OS:$ROOT_DIR" in
        windows:* | wsl:/mnt/*)
            CHOKIDAR_USEPOLLING=${CHOKIDAR_USEPOLLING:-true}
            export CHOKIDAR_USEPOLLING
            ;;
    esac
}

version_at_least() {
    have=$(printf '%s' "$1" | sed 's/^v//')
    have_major=$(printf '%s' "$have" | cut -d. -f1)
    have_minor=$(printf '%s' "$have" | cut -d. -f2)
    case "$have_major" in '' | *[!0-9]*) have_major=0 ;; esac
    case "$have_minor" in '' | *[!0-9]*) have_minor=0 ;; esac
    [ "$have_major" -gt "$2" ] || { [ "$have_major" -eq "$2" ] && [ "$have_minor" -ge "$3" ]; }
}

check_docker() {
    step "Checking Docker"
    command -v docker >/dev/null 2>&1 \
        || fail "Docker is not installed. Install Docker Desktop (Windows, macOS) or Docker Engine (Linux): https://docs.docker.com/get-docker/"
    docker info >/dev/null 2>&1 \
        || fail "Docker is installed but not running. Start Docker Desktop or the Docker service, then run this script again."
    docker compose version >/dev/null 2>&1 \
        || fail "Docker Compose v2 is missing. Update Docker Desktop, or install the docker-compose-plugin package on Linux."
    engine=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo 0)
    compose_version=$(docker compose version --short 2>/dev/null || echo 0)
    version_at_least "$engine" 25 0 || fail "Docker Engine $engine is too old. Version 25 or newer is required."
    version_at_least "$compose_version" 2 21 || fail "Docker Compose $compose_version is too old. Version 2.21 or newer is required."
    ok "Docker $engine with Compose $compose_version ($OS)"
}

random_secret() {
    secret=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom 2>/dev/null | head -c "$1" || true)
    [ "${#secret}" -eq "$1" ] || fail "Could not generate a random secret on this system."
    printf '%s' "$secret"
}

write_env() {
    file=$1
    mode=$2
    if [ "$mode" = prod ]; then
        site=${PUBLIC_URL:-http://localhost}
        files_url=${S3_PUBLIC_URL:-http://localhost:9200}
        cookie_secure=true
    else
        site=http://localhost:5173
        files_url=http://localhost:9200
        cookie_secure=false
    fi
    (
        umask 077
        cat >"$file" <<EOF
POSTGRES_DB=proctor
POSTGRES_USER=proctor_admin
POSTGRES_PASSWORD=$(random_secret 32)

AUTHENTICATOR_USER_NAME=authenticator
AUTHENTICATOR_USER_PASSWORD=$(random_secret 32)
APP_USER_NAME=proctor
APP_USER_PASSWORD=$(random_secret 32)

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/proctor
R2DBC_URL=r2dbc:postgresql://localhost:5433/proctor

JWT_SECRET=$(random_secret 64)
JWT_ISSUER=proctor.local
JWT_ACCESS_EXPIRE_IN_SEC=900
JWT_REFRESH_EXPIRE_IN_SEC=2592000

APP_ALLOWED_ORIGINS=$site
APP_COOKIE_SECURE=$cookie_secure
APP_STORAGE_DIR=./storage

DISCOVERY_SERVER_PORT=8761
DISCOVERY_SERVER_REFRESH_INTERVAL=2

MINIO_ROOT_USER=proctorminio
MINIO_ROOT_PASSWORD=$(random_secret 32)
S3_ACCESS_KEY=proctor-app
S3_SECRET_KEY=$(random_secret 40)
S3_BUCKET=proctor-files
S3_ENDPOINT=http://localhost:9200
S3_PUBLIC_ENDPOINT=$files_url

POSTGRES_PORT=5433
S3_PORT=9200
S3_CONSOLE_PORT=9201

REDIS_HOST=localhost
REDIS_PORT=6390
REDIS_PASSWORD=$(random_secret 32)

NOTIFICATION_SECRET_KEY=$(random_secret 48)

FACE_SERVER_PORT=7059
FACE_DETECTION_MODEL_PATH=./models/det_10g.onnx
FACE_EMBEDDING_MODEL_PATH=./models/w600k_r50.onnx
FACE_DETECTION_INPUT_SIZE=640
FACE_DETECTION_SCORE_THRESHOLD=0.5
FACE_DETECTION_NMS_THRESHOLD=0.4
FACE_MATCH_THRESHOLD=0.45
FACE_MAX_IMAGE_BYTES=8388608
FACE_EMBEDDING_DIMENSIONS=512
EOF
        if [ "$mode" = prod ]; then
            printf '\nHTTP_PORT=80\nAPP_VERSION=latest\n' >>"$file"
        fi
    )
}

ensure_env() {
    if [ "$MODE" = prod ]; then
        file=$PROD_ENV
    else
        file=$DEV_ENV
    fi
    if [ -f "$file" ]; then
        ok "Using existing $(basename "$file")"
        return
    fi
    step "Creating $(basename "$file") with new random secrets"
    write_env "$file" "$MODE"
    ok "Wrote backend/$(basename "$file")"
    if [ "$MODE" = prod ]; then
        warn "Before going live, edit backend/.env.production: set APP_ALLOWED_ORIGINS to your site URL and S3_PUBLIC_ENDPOINT to a file URL browsers can reach."
    fi
}

host_path() {
    if [ "$OS" = windows ] && command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$1"
    else
        printf '%s' "$1"
    fi
}

docker_user() {
    if [ "$OS" = linux ] || [ "$OS" = wsl ]; then
        printf -- '--user %s:%s' "$(id -u)" "$(id -g)"
    fi
}

download() {
    url=$1
    out=$2
    rm -f "$out.part"
    if command -v curl >/dev/null 2>&1; then
        if curl -fL --retry 2 --connect-timeout 20 --progress-bar -o "$out.part" "$url"; then
            mv "$out.part" "$out"
            return 0
        fi
    elif command -v wget >/dev/null 2>&1; then
        if wget -T 20 -t 2 -O "$out.part" "$url"; then
            mv "$out.part" "$out"
            return 0
        fi
    elif MSYS_NO_PATHCONV=1 docker run --rm $(docker_user) -v "$(host_path "$MODELS_DIR"):/models" alpine:3.22 \
        wget -q -T 20 -O /models/buffalo_l.zip.part "$url"; then
        mv "$out.part" "$out"
        return 0
    fi
    rm -f "$out.part"
    return 1
}

ensure_models() {
    if [ -s "$MODELS_DIR/det_10g.onnx" ] && [ -s "$MODELS_DIR/w600k_r50.onnx" ]; then
        ok "Face recognition models present"
        return
    fi
    step "Downloading face recognition models (about 280 MB, one time only)"
    mkdir -p "$MODELS_DIR"
    archive="$MODELS_DIR/buffalo_l.zip"
    if [ ! -s "$archive" ]; then
        download "$MODELS_URL" "$archive" || download "$MODELS_MIRROR" "$archive" \
            || fail "Could not download the face models. Download $MODELS_URL, unzip it, and copy det_10g.onnx and w600k_r50.onnx into backend/face-service/models."
    fi
    MSYS_NO_PATHCONV=1 docker run --rm $(docker_user) -v "$(host_path "$MODELS_DIR"):/models" alpine:3.22 sh -c '
        set -e
        cd /tmp
        unzip -q -o /models/buffalo_l.zip -d pack
        cp "$(find pack -name det_10g.onnx | head -n 1)" /models/det_10g.onnx
        cp "$(find pack -name w600k_r50.onnx | head -n 1)" /models/w600k_r50.onnx
    ' || fail "Could not unpack $archive. Delete it and run ./setup.sh again."
    rm -f "$archive"
    ok "Face recognition models ready"
}

env_value() {
    eval "current=\${$1:-}"
    if [ -n "$current" ]; then
        printf '%s' "$current"
        return
    fi
    if [ -f "$2" ]; then
        value=$(sed -n "s/^$1=//p" "$2" | tail -n 1 | tr -d '\r' | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'\$//")
        if [ -n "$value" ]; then
            printf '%s' "$value"
            return
        fi
    fi
    printf '%s' "$3"
}

compose() {
    if [ "$MODE" = prod ]; then
        docker compose -f docker-compose.prod.yaml --env-file .env.production "$@"
    else
        docker compose -f docker-compose.yaml "$@"
    fi
}

stack_running() {
    [ -n "$(compose ps --status running -q 2>/dev/null)" ]
}

port_busy() {
    if command -v ss >/dev/null 2>&1; then
        ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]$1\$"
        return
    fi
    if command -v lsof >/dev/null 2>&1; then
        lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
        return
    fi
    if command -v netstat >/dev/null 2>&1; then
        netstat -an 2>/dev/null | grep -i listen | grep -Eq "[:.]$1[[:space:]]"
        return
    fi
    return 1
}

check_ports() {
    if stack_running; then
        ok "Stack is already running, it will be updated in place"
        return
    fi
    busy=""
    for entry in "$@"; do
        name=${entry%%=*}
        port=${entry#*=}
        if port_busy "$port"; then
            busy="$busy
  port $port ($name)"
        fi
    done
    if [ -n "$busy" ]; then
        fail "These ports are already in use on this machine:$busy
Stop the program using them, or choose another port, for example: FRONTEND_PORT=5174 ./setup.sh"
    fi
    ok "All required ports are free"
}

seed_admin() {
    if [ -f "$SEED_FILE" ]; then
        grep -o "'[^']*@[^']*'" "$SEED_FILE" 2>/dev/null | head -n 1 | tr -d "'"
    fi
}

start_dev() {
    frontend_port=$(env_value FRONTEND_PORT "$DEV_ENV" 5173)
    gateway_port=$(env_value GATEWAY_SERVER_PORT "$DEV_ENV" 7050)
    discovery_port=$(env_value DISCOVERY_SERVER_PORT "$DEV_ENV" 8761)
    console_port=$(env_value S3_CONSOLE_PORT "$DEV_ENV" 9201)
    FRONTEND_PORT=$frontend_port
    GATEWAY_SERVER_PORT=$gateway_port
    DISCOVERY_SERVER_PORT=$discovery_port
    export FRONTEND_PORT GATEWAY_SERVER_PORT DISCOVERY_SERVER_PORT

    step "Checking ports"
    check_ports \
        "frontend=$frontend_port" \
        "api gateway=$gateway_port" \
        "service registry=$discovery_port" \
        "postgres=$(env_value POSTGRES_PORT "$DEV_ENV" 5433)" \
        "redis=$(env_value REDIS_PORT "$DEV_ENV" 6390)" \
        "minio=$(env_value S3_PORT "$DEV_ENV" 9200)" \
        "minio console=$console_port"

    step "Building and starting the development stack (the first build takes a few minutes)"
    if ! compose up -d --build --remove-orphans --wait --wait-timeout 600; then
        compose ps
        fail "Some services did not become healthy. Inspect them with: ./setup.sh logs <service>"
    fi

    admin=$(seed_admin)
    step "ProctorAI development stack is running"
    printf '  App               http://localhost:%s\n' "$frontend_port"
    printf '  API gateway       http://localhost:%s\n' "$gateway_port"
    printf '  Service registry  http://localhost:%s\n' "$discovery_port"
    printf '  MinIO console     http://localhost:%s\n' "$console_port"
    if [ -n "$admin" ]; then
        printf '  Sign in as        %s (password is in backend/init/07_seed.sql)\n' "$admin"
    fi
    printf '\n  Frontend edits reload instantly. After backend changes run ./setup.sh again.\n'
}

start_prod() {
    http_port=$(env_value HTTP_PORT "$PROD_ENV" 80)
    HTTP_PORT=$http_port
    export HTTP_PORT

    step "Checking ports"
    check_ports \
        "web=$http_port" \
        "minio=$(env_value S3_PORT "$PROD_ENV" 9200)" \
        "minio console=$(env_value S3_CONSOLE_PORT "$PROD_ENV" 9201)"

    step "Building and starting the production stack (a clean build, this takes a few minutes)"
    if ! compose up -d --build --remove-orphans --wait --wait-timeout 900; then
        compose ps
        fail "Some services did not become healthy. Inspect them with: ./setup.sh logs --prod <service>"
    fi

    step "ProctorAI production stack is running"
    if [ "$http_port" = 80 ]; then
        printf '  App  http://localhost\n'
    else
        printf '  App  http://localhost:%s\n' "$http_port"
    fi
    printf '  Settings live in backend/.env.production\n'
}

COMMAND=${1:-dev}
if [ "$#" -gt 0 ]; then
    shift
fi
MODE=dev
SERVICE=""
for arg in "$@"; do
    case "$arg" in
        --prod) MODE=prod ;;
        --dev) MODE=dev ;;
        -*) fail "Unknown option: $arg" ;;
        *) SERVICE=$arg ;;
    esac
done

detect_os

case "$COMMAND" in
    help | -h | --help)
        usage
        ;;
    dev | prod)
        MODE=$COMMAND
        check_docker
        cd "$BACKEND_DIR"
        step "Preparing configuration"
        ensure_env
        ensure_models
        if [ "$MODE" = prod ]; then
            start_prod
        else
            start_dev
        fi
        ;;
    stop | down | status | logs)
        check_docker
        cd "$BACKEND_DIR"
        if [ "$MODE" = prod ] && [ ! -f "$PROD_ENV" ]; then
            fail "The production stack has not been set up yet. Run ./setup.sh prod first."
        fi
        case "$COMMAND" in
            stop) compose stop && ok "Stopped the $MODE stack, all data is kept" ;;
            down) compose down --remove-orphans && ok "Removed the $MODE containers, all data is kept" ;;
            status) compose ps ;;
            logs)
                if [ -n "$SERVICE" ]; then
                    compose logs -f --tail=200 "$SERVICE"
                else
                    compose logs -f --tail=100
                fi
                ;;
        esac
        ;;
    *)
        usage
        fail "Unknown command: $COMMAND"
        ;;
esac
