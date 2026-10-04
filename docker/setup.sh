#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yaml"
DEFAULT_REPOSITORY="vishaltyagi807/proctor-ai"
DEFAULT_VERSION="1.0.0"
ADMIN_EMAIL="admin@college.com"
SEEDED_PASSWORD="admin123"
NATIVE_ORIGINS="tauri://localhost http://tauri.localhost https://tauri.localhost"

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
    cat <<USAGE
Usage: ./setup.sh [command] [options]

Runs the ProctorAI production stack from the prebuilt images on Docker Hub.
Only Docker is required. Nothing is built on this machine.

Commands:
  start             Configure and start the stack (default)
  update [version]  Pull the images for a version (default: the current one) and restart
  env               Only create .env with new random secrets, then exit
  dns               Show the DNS records the domains need and whether they are in place
  status            Show every container and its health
  logs [service]    Follow logs, for example: ./setup.sh logs edge
  stop              Stop the containers and keep all data
  down              Remove the containers and keep all data
  purge             Remove the containers and all volumes, deleting every piece of data
  help              Show this message

Options:
  --yes             Keep the saved domains without asking, or skip the purge confirmation

Environment overrides:
  APP_DOMAIN, FILES_DOMAIN, REGISTRY_DOMAIN, ACME_EMAIL   domains and certificate email
  PUBLIC_IP                                              public IPv4 address of this server
USAGE
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

set_env_key() {
    if grep -q "^$2=" "$1" 2>/dev/null && [ "$(sed -n "s/^$2=//p" "$1" | tail -n 1)" = "$3" ]; then
        return
    fi
    tmp="$1.tmp"
    if ! (
        umask 077
        sed "/^$2=/d" "$1" >"$tmp" && printf '%s=%s\n' "$2" "$3" >>"$tmp"
    ); then
        rm -f "$tmp"
        fail "Could not write $2 to $(basename "$1")."
    fi
    mv "$tmp" "$1" || fail "Could not update $(basename "$1")."
}

ensure_env_key() {
    if grep -q "^$2=." "$1" 2>/dev/null; then
        return
    fi
    set_env_key "$1" "$2" "$3"
    ok "Added $2 to $(basename "$1")"
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

interactive() {
    [ -t 0 ] && [ "$ASSUME_YES" != true ]
}

http_get() {
    if command -v curl >/dev/null 2>&1; then
        if [ -n "${2:-}" ]; then
            curl -fsS --max-time 10 -H "$2" "$1"
        else
            curl -fsS --max-time 10 "$1"
        fi
    elif command -v wget >/dev/null 2>&1; then
        if [ -n "${2:-}" ]; then
            wget -q -T 10 -O - --header="$2" "$1"
        else
            wget -q -T 10 -O - "$1"
        fi
    else
        return 1
    fi
}

is_ipv4() {
    printf '%s\n' "$1" | awk -F. 'NF == 4 { for (i = 1; i <= 4; i++) if ($i !~ /^[0-9]+$/ || $i > 255) exit 1; found = 1 } END { exit found ? 0 : 1 }'
}

is_private_ipv4() {
    case "$1" in
        10.* | 127.* | 192.168.* | 169.254.*) return 0 ;;
        172.*)
            second=$(printf '%s' "$1" | cut -d. -f2)
            [ "$second" -ge 16 ] && [ "$second" -le 31 ]
            return
            ;;
        100.*)
            second=$(printf '%s' "$1" | cut -d. -f2)
            [ "$second" -ge 64 ] && [ "$second" -le 127 ]
            return
            ;;
    esac
    return 1
}

normalize_domain() {
    printf '%s' "$1" | tr 'A-Z' 'a-z' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's#^[a-z]*://##' -e 's#[/:?].*$##' -e 's/\.$//'
}

valid_domain() {
    [ "${#1}" -le 253 ] && printf '%s\n' "$1" | grep -Eq '^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]([a-z0-9-]{0,61}[a-z0-9])?$'
}

valid_email() {
    printf '%s\n' "$1" | grep -Eq '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'
}

parent_domain() {
    case "$1" in
        *.*.*) printf '%s' "${1#*.}" ;;
        *) printf '%s' "$1" ;;
    esac
}

ask_value() {
    if [ -n "$2" ]; then
        printf '  %s [%s]: ' "$1" "$2" >&2
    else
        printf '  %s: ' "$1" >&2
    fi
    read -r reply || reply=""
    case "$reply" in
        "") printf '%s' "$2" ;;
        -) ;;
        *) printf '%s' "$reply" ;;
    esac
}

ask_domain() {
    while :; do
        value=$(normalize_domain "$(ask_value "$1" "$2")")
        if [ -z "$value" ] || valid_domain "$value"; then
            printf '%s' "$value"
            return
        fi
        warn "'$value' is not a valid domain name, for example app.example.com"
    done
}

ask_email() {
    while :; do
        value=$(ask_value "$1" "$2")
        if valid_email "$value"; then
            printf '%s' "$value"
            return
        fi
        warn "Enter a valid email address. Let's Encrypt uses it for certificate expiry notices."
    done
}

configure_domains() {
    step "Configuring domains"
    APP_DOMAIN=$(normalize_domain "$(env_value APP_DOMAIN "$ENV_FILE" "")")
    FILES_DOMAIN=$(normalize_domain "$(env_value FILES_DOMAIN "$ENV_FILE" "")")
    REGISTRY_DOMAIN=$(normalize_domain "$(env_value REGISTRY_DOMAIN "$ENV_FILE" "")")
    ACME_EMAIL=$(env_value ACME_EMAIL "$ENV_FILE" "")

    if interactive; then
        printf '  Enter a domain for each part of the system. Press Enter to keep the value in brackets,\n' >&2
        printf '  type - to clear it, or leave it empty to serve that part from the server IP address.\n\n' >&2
        APP_DOMAIN=$(ask_domain "Web app domain, for example app.example.com" "$APP_DOMAIN")
        base=""
        if [ -n "$APP_DOMAIN" ]; then
            base=$(parent_domain "$APP_DOMAIN")
        fi
        FILES_DOMAIN=$(ask_domain "File storage domain" "${FILES_DOMAIN:-${base:+files.$base}}")
        REGISTRY_DOMAIN=$(ask_domain "Service registry domain" "${REGISTRY_DOMAIN:-${base:+registry.$base}}")
        if [ -n "$APP_DOMAIN$FILES_DOMAIN$REGISTRY_DOMAIN" ]; then
            ACME_EMAIL=$(ask_email "Email for TLS certificate notices" "$ACME_EMAIL")
        fi
    fi

    for entry in "APP_DOMAIN=$APP_DOMAIN" "FILES_DOMAIN=$FILES_DOMAIN" "REGISTRY_DOMAIN=$REGISTRY_DOMAIN"; do
        value=${entry#*=}
        if [ -n "$value" ] && ! valid_domain "$value"; then
            fail "${entry%%=*} '$value' is not a valid domain name."
        fi
    done
    if { [ -n "$APP_DOMAIN" ] && { [ "$APP_DOMAIN" = "$FILES_DOMAIN" ] || [ "$APP_DOMAIN" = "$REGISTRY_DOMAIN" ]; }; } \
        || { [ -n "$FILES_DOMAIN" ] && [ "$FILES_DOMAIN" = "$REGISTRY_DOMAIN" ]; }; then
        fail "The web app, file storage and service registry each need their own domain."
    fi
    if [ -n "$APP_DOMAIN$FILES_DOMAIN$REGISTRY_DOMAIN" ] && ! valid_email "$ACME_EMAIL"; then
        fail "Domains need a contact email for TLS certificates. Run again with ACME_EMAIL=you@example.com ./setup.sh"
    fi
    if [ -n "$APP_DOMAIN" ] && [ -z "$FILES_DOMAIN" ]; then
        warn "HTTPS for the web app also needs a file storage domain, so the web app stays on the IP address."
    fi
    if [ -z "$APP_DOMAIN" ] && [ -n "$FILES_DOMAIN" ]; then
        warn "The file storage domain is only used together with a web app domain."
    fi

    set_env_key "$ENV_FILE" APP_DOMAIN "$APP_DOMAIN"
    set_env_key "$ENV_FILE" FILES_DOMAIN "$FILES_DOMAIN"
    set_env_key "$ENV_FILE" REGISTRY_DOMAIN "$REGISTRY_DOMAIN"
    set_env_key "$ENV_FILE" ACME_EMAIL "$ACME_EMAIL"
    ok "Domains saved in .env"
}

load_domains() {
    APP_DOMAIN=$(normalize_domain "$(env_value APP_DOMAIN "$ENV_FILE" "")")
    FILES_DOMAIN=$(normalize_domain "$(env_value FILES_DOMAIN "$ENV_FILE" "")")
    REGISTRY_DOMAIN=$(normalize_domain "$(env_value REGISTRY_DOMAIN "$ENV_FILE" "")")
    ACME_EMAIL=$(env_value ACME_EMAIL "$ENV_FILE" "")
}

detect_public_ip() {
    for url in https://api.ipify.org https://ipv4.icanhazip.com https://checkip.amazonaws.com; do
        candidate=$(http_get "$url" 2>/dev/null | tr -d ' \r\n' || true)
        if is_ipv4 "$candidate"; then
            printf '%s' "$candidate"
            return 0
        fi
    done
    return 1
}

configure_public_ip() {
    step "Detecting the public IP address"
    PUBLIC_IP=${PUBLIC_IP:-}
    if [ -z "$PUBLIC_IP" ]; then
        PUBLIC_IP=$(detect_public_ip || true)
    fi
    if [ -z "$PUBLIC_IP" ]; then
        PUBLIC_IP=$(sed -n 's/^PUBLIC_IP=//p' "$ENV_FILE" | tail -n 1)
    fi
    if [ -z "$PUBLIC_IP" ] && interactive; then
        PUBLIC_IP=$(ask_value "Public IPv4 address of this server" "")
    fi
    is_ipv4 "$PUBLIC_IP" \
        || fail "Could not detect the public IP address. Run again with PUBLIC_IP=<address> ./setup.sh"
    if is_private_ipv4 "$PUBLIC_IP"; then
        warn "$PUBLIC_IP is a private address. Browsers outside this network and Let's Encrypt cannot reach it."
    fi
    set_env_key "$ENV_FILE" PUBLIC_IP "$PUBLIC_IP"
    ok "Public IP address is $PUBLIC_IP"
}

dns_answers() {
    case "$2" in
        AAAA) code=28 ;;
        *) code=1 ;;
    esac
    for resolver in https://cloudflare-dns.com/dns-query https://dns.google/resolve; do
        if body=$(http_get "$resolver?name=$1&type=$2" "Accept: application/dns-json" 2>/dev/null); then
            printf '%s' "$body" | tr '{' '\n' | grep "\"type\":$code," | sed -n 's/.*"data":"\([^"]*\)".*/\1/p'
            return 0
        fi
    done
    if [ "$2" = A ] && command -v getent >/dev/null 2>&1; then
        getent ahostsv4 "$1" 2>/dev/null | awk '{print $1}' | sort -u
        return 0
    fi
    return 1
}

domain_status() {
    if [ -z "$1" ]; then
        printf 'unset'
        return
    fi
    if ! answers=$(dns_answers "$1" A); then
        printf 'unknown'
        return
    fi
    if [ -z "$answers" ]; then
        printf 'missing'
        return
    fi
    others=$(printf '%s\n' "$answers" | grep -vxF "$PUBLIC_IP" | tr '\n' ' ' | sed 's/ *$//')
    if [ -n "$others" ]; then
        printf 'elsewhere:%s' "$others"
        return
    fi
    printf 'ready'
}

status_text() {
    case "$1" in
        ready) printf 'points to this server' ;;
        missing) printf 'record not found yet' ;;
        unknown) printf 'could not be checked' ;;
        elsewhere:*) printf 'points to %s, change it' "${1#elsewhere:}" ;;
        *) printf '%s' "$1" ;;
    esac
}

report_domain() {
    [ -n "$2" ] || return 0
    if [ "$3" = ready ]; then
        ok "$1 $2 $(status_text "$3")"
    else
        warn "$1 $2 $(status_text "$3")"
    fi
    v6=$(dns_answers "$2" AAAA 2>/dev/null || true)
    if [ -n "$v6" ]; then
        warn "$2 also has an AAAA (IPv6) record. Remove it unless this server answers on that IPv6 address, or certificates can fail."
    fi
}

evaluate_dns() {
    step "Checking DNS records"
    APP_STATUS=$(domain_status "$APP_DOMAIN")
    FILES_STATUS=$(domain_status "$FILES_DOMAIN")
    REGISTRY_STATUS=$(domain_status "$REGISTRY_DOMAIN")
    report_domain "Web app" "$APP_DOMAIN" "$APP_STATUS"
    report_domain "File storage" "$FILES_DOMAIN" "$FILES_STATUS"
    report_domain "Service registry" "$REGISTRY_DOMAIN" "$REGISTRY_STATUS"
    if [ -z "$APP_DOMAIN$FILES_DOMAIN$REGISTRY_DOMAIN" ]; then
        ok "No domains configured, everything is served from $PUBLIC_IP"
    fi
    WEB_MODE=ip
    if [ "$APP_STATUS" = ready ] && [ "$FILES_STATUS" = ready ]; then
        WEB_MODE=domain
    fi
    REGISTRY_MODE=tunnel
    if [ "$REGISTRY_STATUS" = ready ]; then
        REGISTRY_MODE=domain
    fi
}

normalize_origin() {
    printf '%s' "$1" | tr 'A-Z' 'a-z' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's#^\(https\{0,1\}://[^/?#]*\).*$#\1#'
}

valid_origin() {
    printf '%s\n' "$1" | grep -Eq '^https?://(([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?|\[[0-9a-f:]+\])(:[0-9]{1,5})?$'
}

is_managed_origin() {
    host=${1#*://}
    host=${host%%:*}
    case "$host" in
        localhost | 127.0.0.1 | \[::1\]) return 0 ;;
    esac
    is_ipv4 "$host"
}

join_origins() {
    joined=""
    for origin in "$@"; do
        [ -n "$origin" ] || continue
        case ",$joined," in
            *",$origin,"*) ;;
            *) joined=${joined:+$joined,}$origin ;;
        esac
    done
    printf '%s' "$joined"
}

origin_endpoint() {
    scheme=${1%%://*}
    rest=${1#*://}
    case "$rest" in
        *\]:* | [!\[]*:*) printf '%s' "$rest" ;;
        *)
            if [ "$scheme" = https ]; then
                printf '%s:443' "$rest"
            else
                printf '%s:80' "$rest"
            fi
            ;;
    esac
}

blocked_reason() {
    case "$1" in
        files) printf 'it is the file storage endpoint, which serves user uploads. Allowing it would let an uploaded file call the API with a signed-in user'"'"'s cookies' ;;
        registry) printf 'it is the service registry, which never calls the API' ;;
        console) printf 'it is the MinIO console, which never calls the API' ;;
    esac
}

blocked_origin() {
    endpoint=$(origin_endpoint "$1")
    host=${endpoint%:*}
    for entry in ${BLOCKED_HOSTS:-}; do
        if [ "$host" = "${entry%%=*}" ]; then
            blocked_reason "${entry#*=}"
            return 0
        fi
    done
    for entry in ${BLOCKED_ENDPOINTS:-}; do
        if [ "$endpoint" = "${entry%%=*}" ]; then
            blocked_reason "${entry#*=}"
            return 0
        fi
    done
    return 1
}

local_endpoints() {
    for local_host in localhost 127.0.0.1; do
        printf '%s:%s=files %s:%s=console %s:%s=registry ' \
            "$local_host" "$1" "$local_host" "$2" "$local_host" "$3"
    done
}

extra_origins() {
    set -f
    if grep -q "^APP_EXTRA_ORIGINS=" "$1" 2>/dev/null || [ -n "${APP_EXTRA_ORIGINS:-}" ]; then
        raw=$(env_value APP_EXTRA_ORIGINS "$1" "")
    else
        raw=""
        for origin in $(sed -n 's/^APP_ALLOWED_ORIGINS=//p' "$1" | tail -n 1 | tr ',' ' '); do
            origin=$(normalize_origin "$origin")
            if valid_origin "$origin" && ! is_managed_origin "$origin" && [ "$origin" != "https://yourdomain.com" ]; then
                if reason=$(blocked_origin "$origin"); then
                    warn "Dropped $origin from the allowed origins: $reason."
                else
                    raw=${raw:+$raw,}$origin
                fi
            fi
        done
    fi
    cleaned=""
    for origin in $(printf '%s' "$raw" | tr ',' ' '); do
        origin=$(normalize_origin "$origin")
        valid_origin "$origin" \
            || fail "APP_EXTRA_ORIGINS has an invalid origin '$origin'. Use the form https://site.example.com"
        if reason=$(blocked_origin "$origin"); then
            fail "APP_EXTRA_ORIGINS must not include $origin: $reason."
        fi
        cleaned=$(join_origins $(printf '%s' "$cleaned" | tr ',' ' ') "$origin")
    done
    set +f
    printf '%s' "$cleaned"
}

manage_origins() {
    file=$1
    shift
    extras=$(extra_origins "$file")
    set_env_key "$file" APP_EXTRA_ORIGINS "$extras"
    set -f
    ALLOWED_ORIGINS=$(join_origins "$@" $(printf '%s' "$extras" | tr ',' ' '))
    set +f
    previous=$(sed -n 's/^APP_ALLOWED_ORIGINS=//p' "$file" | tail -n 1)
    set_env_key "$file" APP_ALLOWED_ORIGINS "$ALLOWED_ORIGINS"
    if [ "$previous" != "$ALLOWED_ORIGINS" ]; then
        ok "Allowed origins updated: $ALLOWED_ORIGINS"
    else
        ok "Allowed origins: $ALLOWED_ORIGINS"
    fi
}

apply_endpoints() {
    FILES_PORT=$(env_value S3_PORT "$ENV_FILE" 9200)
    if [ "$WEB_MODE" = domain ]; then
        WEB_URL="https://$APP_DOMAIN"
        FILES_URL="https://$FILES_DOMAIN"
        cookie_secure=true
    else
        WEB_URL="http://$PUBLIC_IP"
        FILES_URL="http://$PUBLIC_IP:$FILES_PORT"
        cookie_secure=false
    fi
    BLOCKED_HOSTS=""
    if [ -n "$FILES_DOMAIN" ]; then
        BLOCKED_HOSTS="$FILES_DOMAIN=files"
    fi
    if [ -n "$REGISTRY_DOMAIN" ]; then
        BLOCKED_HOSTS="$BLOCKED_HOSTS $REGISTRY_DOMAIN=registry"
    fi
    BLOCKED_ENDPOINTS="$PUBLIC_IP:$FILES_PORT=files $(local_endpoints "$FILES_PORT" \
        "$(env_value S3_CONSOLE_PORT "$ENV_FILE" 9201)" "$(env_value DISCOVERY_SERVER_PORT "$ENV_FILE" 8761)")"
    manage_origins "$ENV_FILE" "$WEB_URL" $NATIVE_ORIGINS
    set_env_key "$ENV_FILE" APP_COOKIE_SECURE "$cookie_secure"
    set_env_key "$ENV_FILE" S3_PUBLIC_ENDPOINT "$FILES_URL"
    set_env_key "$ENV_FILE" MINIO_CORS_ALLOW_ORIGIN "$ALLOWED_ORIGINS"
}

write_caddyfile() {
    mkdir -p "$ROOT_DIR/edge" || fail "Could not create edge."
    target="$ROOT_DIR/edge/Caddyfile"
    tmp="$target.tmp"
    if ! {
        printf '{\n'
        if [ -n "$ACME_EMAIL" ]; then
            printf '\temail %s\n' "$ACME_EMAIL"
        fi
        cat <<'EOF'
	admin off
	servers {
		protocols h1 h2 h3
	}
}

(common) {
	header {
		-Server
		-Via
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
		defer
	}
	log {
		output stdout
		format json
	}
}

(hsts) {
	header Strict-Transport-Security "max-age=31536000"
}

(web) {
	import common
	request_body {
		max_size 300MB
	}
	reverse_proxy frontend:80
}

(files) {
	import common
	request_body {
		max_size 250MB
	}
	reverse_proxy minio:9000 {
		flush_interval -1
	}
}

(registry) {
	import common
	@dashboard path / /lastn /eureka/css/* /eureka/js/* /eureka/fonts/* /eureka/images/* /actuator/health
	handle @dashboard {
		reverse_proxy discovery-server:8761
	}
	handle {
		respond 404
	}
}

http://:8081 {
	respond /healthz "ok" 200
	respond 404
}

http://:8761 {
	import registry
}

EOF
        if [ "$WEB_MODE" = domain ]; then
            printf '%s {\n\timport hsts\n\timport web\n}\n\n' "$APP_DOMAIN"
            printf '%s {\n\timport hsts\n\timport files\n}\n\n' "$FILES_DOMAIN"
            printf 'http:// {\n\tredir https://%s{uri} permanent\n}\n\n' "$APP_DOMAIN"
        else
            printf 'http://:80 {\n\timport web\n}\n\n'
            printf 'http://:9200 {\n\timport files\n}\n\n'
        fi
        if [ "$REGISTRY_MODE" = domain ]; then
            printf '%s {\n\timport hsts\n\timport registry\n}\n' "$REGISTRY_DOMAIN"
        fi
    } >"$tmp"; then
        rm -f "$tmp"
        fail "Could not write edge/Caddyfile."
    fi
    mv "$tmp" "$target" || fail "Could not write edge/Caddyfile."
}

verify_https() {
    if ! command -v curl >/dev/null 2>&1; then
        warn "curl is not installed, so the HTTPS check for $1 was skipped."
        return 0
    fi
    attempt=0
    while [ "$attempt" -lt 36 ]; do
        if curl -fsS --max-time 5 --resolve "$1:443:127.0.0.1" -o /dev/null "https://$1$2" 2>/dev/null; then
            ok "HTTPS certificate for $1 is active"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 5
    done
    warn "HTTPS for $1 is not working yet. Make sure TCP 80 and 443 are open to the internet, then check: ./setup.sh logs edge"
    return 1
}

print_dns_records() {
    pending=false
    for entry in "$APP_DOMAIN:$APP_STATUS" "$FILES_DOMAIN:$FILES_STATUS" "$REGISTRY_DOMAIN:$REGISTRY_STATUS"; do
        if [ -n "${entry%%:*}" ] && [ "${entry#*:}" != ready ]; then
            pending=true
        fi
    done
    if [ -z "$APP_DOMAIN$FILES_DOMAIN$REGISTRY_DOMAIN" ]; then
        printf '\n  No domains are configured. To add them with automatic HTTPS, run ./setup.sh again.\n'
        return
    fi
    if [ "$pending" = true ]; then
        printf '\n%sAdd these records at your DNS provider to finish the setup:%s\n\n' "$BOLD" "$RESET"
    else
        printf '\n%sDNS records (all in place):%s\n\n' "$BOLD" "$RESET"
    fi
    printf '    %-6s %-40s %-26s %s\n' Type Name Value Status
    for entry in "$APP_DOMAIN:$APP_STATUS" "$FILES_DOMAIN:$FILES_STATUS" "$REGISTRY_DOMAIN:$REGISTRY_STATUS"; do
        name=${entry%%:*}
        [ -n "$name" ] || continue
        printf '    %-6s %-40s %-26s %s\n' A "$name" "$PUBLIC_IP" "$(status_text "${entry#*:}")"
    done
    root=$(parent_domain "${APP_DOMAIN:-${FILES_DOMAIN:-$REGISTRY_DOMAIN}}")
    printf '    %-6s %-40s %-26s %s\n' CAA "$root" '0 issue "letsencrypt.org"' "optional, recommended"
    printf '    %-6s %-40s %-26s %s\n' CAA "$root" '0 issue "sectigo.com"' "optional, allows the ZeroSSL fallback"
    printf '\n  Also allow inbound TCP 80, TCP 443 and UDP 443 in your firewall or cloud security group.\n'
    if [ "$pending" = true ]; then
        printf '  DNS changes usually apply within minutes but can take up to 48 hours.\n'
        printf '  Check progress with ./setup.sh dns, then run ./setup.sh again to switch to HTTPS.\n'
    fi
}

write_env() {
    (
        umask 077
        cat >"$ENV_FILE" <<ENV
IMAGE_REPOSITORY=$DEFAULT_REPOSITORY
PROCTOR_VERSION=$DEFAULT_VERSION

POSTGRES_DB=proctor
POSTGRES_USER=proctor_admin
POSTGRES_PASSWORD=$(random_secret 32)
AUTHENTICATOR_USER_NAME=authenticator
AUTHENTICATOR_USER_PASSWORD=$(random_secret 32)
APP_USER_NAME=proctor
APP_USER_PASSWORD=$(random_secret 32)

JWT_SECRET=$(random_secret 64)
JWT_ISSUER=proctor.local
JWT_ACCESS_EXPIRE_IN_SEC=900
JWT_REFRESH_EXPIRE_IN_SEC=2592000

DISCOVERY_USERNAME=proctor-discovery
DISCOVERY_PASSWORD=$(random_secret 48)
DISCOVERY_SERVER_PORT=8761

MINIO_ROOT_USER=proctorminio
MINIO_ROOT_PASSWORD=$(random_secret 32)
S3_ACCESS_KEY=proctor-app
S3_SECRET_KEY=$(random_secret 40)
S3_BUCKET=proctor-files
S3_PORT=9200
S3_CONSOLE_PORT=9201

REDIS_PASSWORD=$(random_secret 32)
NOTIFICATION_SECRET_KEY=$(random_secret 48)

FACE_DETECTION_INPUT_SIZE=640
FACE_DETECTION_SCORE_THRESHOLD=0.5
FACE_DETECTION_NMS_THRESHOLD=0.4
FACE_MATCH_THRESHOLD=0.45
FACE_MAX_IMAGE_BYTES=8388608
FACE_EMBEDDING_DIMENSIONS=512
ENV
    ) || fail "Could not write .env."
}

ensure_env() {
    if [ -f "$ENV_FILE" ]; then
        ok "Using existing .env"
        ensure_env_key "$ENV_FILE" IMAGE_REPOSITORY "$DEFAULT_REPOSITORY"
        ensure_env_key "$ENV_FILE" PROCTOR_VERSION "$DEFAULT_VERSION"
        return
    fi
    step "Creating .env with new random secrets"
    write_env
    ok "Wrote .env (keep a copy somewhere safe)"
}

compose() {
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

stack_running() {
    [ -n "$(compose ps --status running -q 2>/dev/null)" ]
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
Stop the program using them, then run ./setup.sh again."
    fi
    ok "All required ports are free"
}

sync_init() {
    source_dir="$ROOT_DIR/../backend/init"
    if [ -d "$source_dir" ]; then
        rm -rf "$ROOT_DIR/init.tmp"
        mkdir -p "$ROOT_DIR/init.tmp" && cp -p "$source_dir"/* "$ROOT_DIR/init.tmp/" \
            || fail "Could not copy the database scripts from backend/init."
        rm -rf "$ROOT_DIR/init"
        mv "$ROOT_DIR/init.tmp" "$ROOT_DIR/init" || fail "Could not update the init folder."
        ok "Database scripts synced from backend/init"
    fi
    ls "$ROOT_DIR"/init/*.sql >/dev/null 2>&1 \
        || fail "The init folder with the database scripts is missing. Copy it next to setup.sh."
}

configure() {
    configure_domains
    configure_public_ip
    evaluate_dns
    apply_endpoints
    write_caddyfile
    ok "Edge proxy configured: web app $WEB_URL, file storage $FILES_URL"
}

pull_images() {
    repository=$(env_value IMAGE_REPOSITORY "$ENV_FILE" "$DEFAULT_REPOSITORY")
    version=$(env_value PROCTOR_VERSION "$ENV_FILE" "$DEFAULT_VERSION")
    step "Pulling ProctorAI $version from $repository"
    compose pull --quiet \
        || fail "Could not pull the images. Check the internet connection. If the repository is private, run docker login first."
    ok "Images ready"
}

database_query() {
    printf '%s\n' "$1" | compose exec -T postgres sh -c 'psql -tAq -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
}

secure_admin() {
    default=$(database_query "select count(*) from users where email = '$ADMIN_EMAIL' and password = crypt('$SEEDED_PASSWORD', password);" 2>/dev/null | tr -d '[:space:]' || true)
    if [ "$default" != 1 ]; then
        return 0
    fi
    password=$(random_secret 20)
    if database_query "update users set password = crypt('$password', gen_salt('bf', 12)) where email = '$ADMIN_EMAIL';" >/dev/null; then
        set_env_key "$ENV_FILE" ADMIN_EMAIL "$ADMIN_EMAIL"
        set_env_key "$ENV_FILE" ADMIN_PASSWORD "$password"
        NEW_ADMIN_PASSWORD=$password
        ok "Replaced the default administrator password with a random one"
    else
        warn "Could not replace the default administrator password. Sign in as $ADMIN_EMAIL and change it now."
    fi
}

print_summary() {
    user=$(env_value DISCOVERY_USERNAME "$ENV_FILE" proctor-discovery)
    tunnel="ssh -L 8761:127.0.0.1:8761 -L 9201:127.0.0.1:9201 <user>@$PUBLIC_IP"
    step "ProctorAI $(env_value PROCTOR_VERSION "$ENV_FILE" "$DEFAULT_VERSION") is running"
    if [ "$WEB_MODE" = domain ]; then
        printf '  Web app           %s\n' "$WEB_URL"
        printf '  File storage      %s\n' "$FILES_URL"
    else
        printf '  Web app           %s   (temporary IP address, not encrypted)\n' "$WEB_URL"
        printf '  File storage      %s   (temporary IP address, not encrypted)\n' "$FILES_URL"
        if [ -n "$APP_DOMAIN" ] && [ -n "$FILES_DOMAIN" ]; then
            printf '  After DNS is set  https://%s and https://%s\n' "$APP_DOMAIN" "$FILES_DOMAIN"
        fi
    fi
    if [ "$REGISTRY_MODE" = domain ]; then
        printf '  Service registry  https://%s\n' "$REGISTRY_DOMAIN"
    else
        printf '  Service registry  http://localhost:8761 through an SSH tunnel: %s\n' "$tunnel"
        if [ -n "$REGISTRY_DOMAIN" ]; then
            printf '  After DNS is set  https://%s\n' "$REGISTRY_DOMAIN"
        fi
    fi
    printf '                    sign in as %s, the password is DISCOVERY_PASSWORD in .env\n' "$user"
    printf '  MinIO console     http://localhost:9201 through the same SSH tunnel\n'
    if [ -n "${NEW_ADMIN_PASSWORD:-}" ]; then
        printf '  Administrator     %s / %s%s%s\n' "$ADMIN_EMAIL" "$BOLD" "$NEW_ADMIN_PASSWORD" "$RESET"
        printf '                    shown once and saved as ADMIN_PASSWORD in .env. Change it after signing in.\n'
    elif [ -n "$(env_value ADMIN_PASSWORD "$ENV_FILE" "")" ]; then
        printf '  Administrator     %s, the initial password is ADMIN_PASSWORD in .env\n' "$ADMIN_EMAIL"
    else
        printf '  Administrator     %s\n' "$ADMIN_EMAIL"
    fi
    printf '  Settings          .env\n'
    print_dns_records
}

start() {
    step "Preparing configuration"
    ensure_env
    sync_init
    configure
    check_docker
    step "Checking ports"
    check_ports \
        "http=80" \
        "https=443" \
        "file storage=$(env_value S3_PORT "$ENV_FILE" 9200)" \
        "service registry=$(env_value DISCOVERY_SERVER_PORT "$ENV_FILE" 8761)" \
        "minio console=$(env_value S3_CONSOLE_PORT "$ENV_FILE" 9201)"
    pull_images
    step "Starting the stack"
    if ! compose up -d --remove-orphans --wait --wait-timeout 900; then
        compose ps
        fail "Some services did not become healthy. Inspect them with: ./setup.sh logs <service>"
    fi
    secure_admin
    if [ "$WEB_MODE" = domain ] || [ "$REGISTRY_MODE" = domain ]; then
        step "Waiting for TLS certificates"
        if [ "$WEB_MODE" = domain ]; then
            verify_https "$APP_DOMAIN" /healthz || true
            verify_https "$FILES_DOMAIN" /minio/health/live || true
        fi
        if [ "$REGISTRY_MODE" = domain ]; then
            verify_https "$REGISTRY_DOMAIN" /actuator/health || true
        fi
    fi
    print_summary
}

confirm_purge() {
    if [ "$ASSUME_YES" = true ]; then
        return
    fi
    [ -t 0 ] || fail "Refusing to delete data without confirmation. Run again with --yes to purge non-interactively."
    warn "This deletes the database, Redis data, uploaded files, face enrollments and TLS certificates. It cannot be undone."
    printf 'Type yes to continue: '
    read -r answer || answer=""
    [ "$answer" = yes ] || fail "Purge cancelled, nothing was removed."
}

require_env() {
    [ -f "$ENV_FILE" ] || fail "ProctorAI has not been set up here yet. Run ./setup.sh first."
}

COMMAND=${1:-start}
if [ "$#" -gt 0 ]; then
    shift
fi
ASSUME_YES=false
ARGUMENT=""
for arg in "$@"; do
    case "$arg" in
        --yes | -y) ASSUME_YES=true ;;
        -*) fail "Unknown option: $arg" ;;
        *) ARGUMENT=$arg ;;
    esac
done

detect_os
cd "$ROOT_DIR"

case "$COMMAND" in
    help | -h | --help)
        usage
        ;;
    start)
        start
        ;;
    update)
        require_env
        if [ -n "$ARGUMENT" ]; then
            printf '%s\n' "$ARGUMENT" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$' || fail "Invalid version: $ARGUMENT"
            set_env_key "$ENV_FILE" PROCTOR_VERSION "$ARGUMENT"
        fi
        ASSUME_YES=true
        start
        ;;
    env)
        step "Preparing configuration"
        ensure_env
        ;;
    dns)
        require_env
        load_domains
        configure_public_ip
        evaluate_dns
        if [ "$WEB_MODE" = domain ]; then
            printf '\n  The web app and file storage are ready for HTTPS. Run ./setup.sh to switch.\n'
        fi
        print_dns_records
        ;;
    status | logs | stop | down | purge)
        require_env
        check_docker
        case "$COMMAND" in
            status) compose ps ;;
            logs)
                if [ -n "$ARGUMENT" ]; then
                    compose logs -f --tail=200 "$ARGUMENT"
                else
                    compose logs -f --tail=100
                fi
                ;;
            stop) compose stop && ok "Stopped the stack, all data is kept" ;;
            down) compose down --remove-orphans && ok "Removed the containers, all data is kept" ;;
            purge)
                confirm_purge
                step "Removing the containers and volumes"
                compose down --volumes --remove-orphans || fail "Could not remove the stack."
                ok "Removed the containers and deleted all of their data"
                ;;
        esac
        ;;
    *)
        usage
        fail "Unknown command: $COMMAND"
        ;;
esac
