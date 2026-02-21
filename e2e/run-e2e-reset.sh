#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# multitenancy001 - E2E RESET + RUN (Postgres local)
#
# Goals:
# - Drop and recreate the database
# - Start the Spring Boot app
# - Wait for STARTED + /actuator/health
# - Run Postman collection via Newman
# - Stop the app (even on error)
#
# Usage:
#   COLLECTION=e2e/<collection.json> \
#   ENV_FILE=e2e/<env.json> \
#   ./e2e/run-e2e-reset.sh
#
# Optional overrides:
#   DB_HOST=localhost DB_PORT=5432 DB_USER=postgres DB_PASSWORD=admin DB_NAME=db_multitenancy
#   BASE_URL=http://localhost:8080    (optional; overrides env parsing)
#   APP_PORT=8080                     (optional; used for port-check)
# =========================================================

# ---------------------------------------------------------
# Postgres connection (psql) configuration
# ---------------------------------------------------------
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-db_multitenancy}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-admin}"

# Keep PG* aligned with DB_* (avoid accidental overrides)
export PGHOST="${PGHOST:-$DB_HOST}"
export PGPORT="${PGPORT:-$DB_PORT}"
export PGDATABASE="${PGDATABASE:-$DB_NAME}"
export PGUSER="${PGUSER:-$DB_USER}"
export PGPASSWORD="${PGPASSWORD:-$DB_PASSWORD}"

psql_cmd() {
  # Method-level comment: run psql fail-fast and quieter.
  psql -v ON_ERROR_STOP=1 -q "$@"
}

psql_auth_hint() {
  # Method-level comment: print actionable auth hints.
  cat <<'EOF'
[HINT] Falha de autenticação no Postgres (usuário/senha).
       Este script usa por default:
         DB_USER=postgres
         DB_PASSWORD=admin

       Ajuste assim (exemplo):
         DB_PASSWORD=SUA_SENHA ./e2e/run-e2e-reset.sh

       Ou crie um arquivo ~/.pgpass (recomendado) e não passe senha na linha de comando.

       Verifique também se o Postgres está aceitando senha (pg_hba.conf) e se a senha do usuário está correta.
EOF
}

# ---------------------------------------------------------
# Paths
# ---------------------------------------------------------
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
E2E_DIR="${ROOT_DIR}/e2e"

COLLECTION="${COLLECTION:-${E2E_DIR}/multitenancy001.postman_collection.v15.4-categories-subcategories-suppliers-products-full.json}"
ENV_FILE="${ENV_FILE:-${E2E_DIR}/multitenancy001.local.postman_environment.v15.4.json}"

APP_LOG="${ROOT_DIR}/.e2e-app.log"
APP_PID=""

APP_PORT="${APP_PORT:-8080}"

log() { printf "%s\n" "$*"; }

require_cmd() {
  # Method-level comment: checks required tools are available in PATH (or as executable path).
  local cmd="$1"
  if [[ "${cmd}" == ./* || "${cmd}" == */* ]]; then
    if [[ ! -x "${cmd}" ]]; then
      log "ERROR: executable not found or not executable: ${cmd}"
      return 1
    fi
    return 0
  fi
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    log "ERROR: command not found: ${cmd}"
    return 1
  fi
  return 0
}

try_find_psql_windows() {
  # Method-level comment: tries to locate psql.exe in common Windows paths (Git Bash).
  local found=""
  for p in /c/Program\ Files/PostgreSQL/*/bin/psql.exe /c/Program\ Files/PostgreSQL/*/bin/psql; do
    if [[ -f "${p}" ]]; then
      found="${p}"
    fi
  done
  if [[ -n "${found}" ]]; then
    export PATH="$(dirname "${found}"):${PATH}"
    return 0
  fi
  return 1
}

ensure_psql() {
  # Method-level comment: ensures 'psql' is available; attempts auto-detection on Windows.
  if command -v psql >/dev/null 2>&1; then
    return 0
  fi

  log "INFO: psql not found in PATH; trying Windows auto-detection..."
  if try_find_psql_windows && command -v psql >/dev/null 2>&1; then
    log "INFO: psql found via auto-detection."
    return 0
  fi

  log "ERROR: psql not found."
  log "Fix options:"
  log "  1) Install PostgreSQL client tools (psql)."
  log "  2) Add PostgreSQL 'bin' to PATH."
  log "     Example (Git Bash):"
  log "       export PATH=\"/c/Program Files/PostgreSQL/18/bin:$PATH\""
  return 1
}

cleanup() {
  # Method-level comment: stop Spring Boot app if it was started.
  if [[ -n "${APP_PID}" ]]; then
    log "==> Stopping app (pid=${APP_PID})..."
    kill "${APP_PID}" >/dev/null 2>&1 || true
    wait "${APP_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

is_port_in_use() {
  # Method-level comment: checks if APP_PORT is in use (Windows Git Bash friendly).
  if command -v lsof >/dev/null 2>&1; then
    if lsof -iTCP:"${APP_PORT}" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
      return 0
    fi
    return 1
  fi

  if command -v netstat >/dev/null 2>&1; then
    if netstat -an 2>/dev/null | grep -E "[:.]${APP_PORT}[[:space:]].*LISTEN" >/dev/null 2>&1; then
      return 0
    fi
    return 1
  fi

  return 1
}

print_port_hint() {
  # Method-level comment: shows how to free port 8080 (Windows + Linux).
  cat <<EOF
[HINT] A porta ${APP_PORT} parece estar em uso.

Windows (PowerShell):
  netstat -ano | findstr :${APP_PORT}
  taskkill /PID <PID> /F

Windows (cmd):
  netstat -ano | findstr :${APP_PORT}
  taskkill /PID <PID> /F

Linux/macOS:
  lsof -i :${APP_PORT}
  kill -9 <PID>
EOF
}

# ---------------------------------------------------------
# Start
# ---------------------------------------------------------
log "==> E2E RESET + RUN (Postgres local)"

# Validate inputs
if [[ ! -f "${COLLECTION}" ]]; then
  log "ERROR: COLLECTION file not found: ${COLLECTION}"
  exit 2
fi
if [[ ! -f "${ENV_FILE}" ]]; then
  log "ERROR: ENV_FILE file not found: ${ENV_FILE}"
  exit 2
fi

# Ensure required tools
require_cmd ./mvnw || { log "ERROR: ./mvnw not found at repo root"; exit 2; }
require_cmd node || { log "ERROR: Node.js is required for Newman"; exit 2; }
require_cmd newman || { log "ERROR: Newman not found. Install: npm i -g newman"; exit 2; }
require_cmd curl || { log "ERROR: curl is required for health check"; exit 2; }
require_cmd python || { log "ERROR: python is required for ENV parsing"; exit 2; }
ensure_psql || { log "ERROR: cannot continue without psql."; exit 2; }

# Preflight port check
if is_port_in_use; then
  log "ERROR: port ${APP_PORT} is already in use (before starting app)."
  print_port_hint
  exit 6
fi

# Drop/recreate DB
log "==> Dropping database '${PGDATABASE}' and recreating..."
psql_cmd -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d postgres -v ON_ERROR_STOP=1 <<SQL || { psql_auth_hint; exit 1; }
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${PGDATABASE}';
DROP DATABASE IF EXISTS "${PGDATABASE}";
CREATE DATABASE "${PGDATABASE}";
SQL
log "==> DB recreated: ${PGDATABASE}"

# Start the app
log "==> Starting app: ./mvnw -DskipTests spring-boot:run"
: > "${APP_LOG}"
( cd "${ROOT_DIR}" && ./mvnw -DskipTests spring-boot:run > "${APP_LOG}" 2>&1 ) &
APP_PID="$!"
log "==> App started (pid=${APP_PID}). Logs: ${APP_LOG}"

# Wait for STARTED line
log "==> Waiting for Spring Boot 'Started ...Application' log line..."
timeout_s=180
start_ts="$(date +%s)"
while true; do
  if grep -qE "Started .*Application" "${APP_LOG}" 2>/dev/null; then
    log "==> App log indicates STARTED."
    break
  fi
  now_ts="$(date +%s)"
  if (( now_ts - start_ts > timeout_s )); then
    log "ERROR: app did not start within ${timeout_s}s. Last 120 lines:"
    tail -n 120 "${APP_LOG}" || true
    if grep -q "Port ${APP_PORT} was already in use" "${APP_LOG}" 2>/dev/null; then
      print_port_hint
    fi
    exit 3
  fi
  sleep 1
done

# Determine base_url
if [[ -n "${BASE_URL:-}" ]]; then
  resolved_base_url="${BASE_URL}"
else
  resolved_base_url="$(ENV_FILE_PATH="${ENV_FILE}" python - <<'PY'
import json, os
p = os.environ.get("ENV_FILE_PATH")
if not p:
    raise SystemExit("ENV_FILE_PATH not set")
with open(p, "r", encoding="utf-8") as f:
    d = json.load(f)
vals = {v.get("key"): v.get("value") for v in d.get("values", [])}
print(vals.get("base_url", "http://localhost:8080"))
PY
)"
fi

log "==> Using base_url: ${resolved_base_url}"

# Health check
log "==> Waiting for health at ${resolved_base_url}/actuator/health ..."
health_timeout_s=90
health_start="$(date +%s)"
while true; do
  if curl -fsS "${resolved_base_url}/actuator/health" >/dev/null 2>&1; then
    log "==> Health OK."
    break
  fi
  now_ts="$(date +%s)"
  if (( now_ts - health_start > health_timeout_s )); then
    log "ERROR: health check did not become OK within ${health_timeout_s}s. Last 120 lines:"
    tail -n 120 "${APP_LOG}" || true
    exit 4
  fi
  sleep 1
done

log "==> Warm-up (2s) ..."
sleep 2

# Run Newman
log "==> Running Newman..."
newman run "${COLLECTION}" -e "${ENV_FILE}" --bail

log "==> Newman OK."
log "==> DONE ✅"
