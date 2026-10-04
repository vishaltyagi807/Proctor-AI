#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

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
Usage: ./clean-up.sh [--dry-run] [--yes]

Deletes generated files so the project is back to a clean checkout:
  backend   every Gradle build/ and .gradle/ folder, .env and .env.production
  frontend  node_modules and the Tauri build output (src-tauri/target)

Options:
  --dry-run   Only list what would be deleted
  --yes       Delete without asking for confirmation

Deleting .env or .env.production removes the secrets that existing database volumes
were created with. Run ./setup.sh purge (and ./setup.sh purge --prod) first if you
also want a fresh database, or keep a copy of those files.
EOF
}

DRY_RUN=false
ASSUME_YES=false
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        --yes | -y) ASSUME_YES=true ;;
        help | -h | --help)
            usage
            exit 0
            ;;
        *)
            usage
            fail "Unknown option: $arg"
            ;;
    esac
done

[ -d "$BACKEND_DIR" ] && [ -d "$FRONTEND_DIR" ] || fail "Run this script from the ProctorAI repository."

TARGETS=""
add_target() {
    [ -e "$1" ] || return 0
    TARGETS="$TARGETS
$1"
}

for gradle_file in "$BACKEND_DIR/build.gradle.kts" "$BACKEND_DIR"/*/build.gradle.kts; do
    [ -f "$gradle_file" ] || continue
    module_dir=$(dirname "$gradle_file")
    add_target "$module_dir/build"
    add_target "$module_dir/.gradle"
done
add_target "$BACKEND_DIR/.gradle"
for env_file in .env .env.production .env.tmp .env.production.tmp; do
    add_target "$BACKEND_DIR/$env_file"
done
add_target "$FRONTEND_DIR/node_modules"
add_target "$FRONTEND_DIR/src-tauri/target"

TARGETS=$(printf '%s\n' "$TARGETS" | sed '/^$/d' | sort -u)

if [ -z "$TARGETS" ]; then
    ok "Nothing to clean, the project is already clean"
    exit 0
fi

step "Files and folders to delete"
while IFS= read -r target; do
    size=$(du -sh "$target" 2>/dev/null | cut -f1)
    printf '  %-8s %s\n' "${size:-?}" "${target#"$ROOT_DIR"/}"
done <<LIST
$TARGETS
LIST
total_kb=$(printf '%s\n' "$TARGETS" | tr '\n' '\0' | xargs -0 du -sk 2>/dev/null | awk '{ sum += $1 } END { print sum + 0 }')
printf '  %s%-8s total%s\n' "$BOLD" "$(awk -v kb="$total_kb" 'BEGIN { if (kb >= 1048576) printf "%.1fG", kb / 1048576; else if (kb >= 1024) printf "%.0fM", kb / 1024; else printf "%dK", kb }')" "$RESET"

if printf '%s\n' "$TARGETS" | grep -Eq '/\.env(\.production)?$'; then
    volumes=""
    if command -v docker >/dev/null 2>&1; then
        volumes=$(docker volume ls --format '{{.Name}}' 2>/dev/null | grep -E '^proctor-(backend|prod)_postgres_data$' || true)
    fi
    if [ -n "$volumes" ]; then
        warn "These database volumes were created with the secrets in the .env files being deleted:"
        printf '%s\n' "$volumes" | sed 's/^/         /' >&2
        warn "After clean-up, ./setup.sh creates new secrets that cannot open them. Run ./setup.sh purge (and ./setup.sh purge --prod) first for a fresh start, or keep a copy of the .env files."
    else
        warn "The .env files hold the generated secrets. ./setup.sh creates new ones on the next run."
    fi
fi

if [ "$DRY_RUN" = true ]; then
    ok "Dry run, nothing was deleted"
    exit 0
fi

if [ "$ASSUME_YES" != true ]; then
    [ -t 0 ] || fail "Refusing to delete without confirmation. Run again with --yes, or with --dry-run to only list."
    printf '\nType yes to delete these: '
    read -r answer || answer=""
    [ "$answer" = yes ] || fail "Clean-up cancelled, nothing was deleted."
fi

step "Deleting"
failed=0
while IFS= read -r target; do
    if rm -rf -- "$target" 2>/dev/null && [ ! -e "$target" ]; then
        ok "Deleted ${target#"$ROOT_DIR"/}"
    else
        warn "Could not delete ${target#"$ROOT_DIR"/} (check its permissions)"
        failed=1
    fi
done <<LIST
$TARGETS
LIST

if [ "$failed" -ne 0 ]; then
    fail "Some items could not be deleted."
fi
ok "Clean-up finished. Run ./setup.sh to create new secrets and rebuild, and npm ci in frontend/ to reinstall packages."
