#!/bin/bash
set -euo pipefail

# Runner: run-e2e-reset.sh (v10.9.16)
#
# Goals:
# - Drop and recreate DB
# - Start Spring Boot app and wait until READY
# - Create an "effective" Postman env JSON ensuring required vars exist
# - Run Newman with basic guards (timeout + app death)
#
# Notes:
# - Avoid non-ASCII output (no accents/emojis) to reduce encoding issues on Windows Git Bash.

VERSION="v10.9.16"

die() {
  echo "[ERROR] $*" >&2
  exit 1
}

info() {
  echo "==> $*"
}

warn() {
  echo "[WARN] $*"
}

# Resolve directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Inputs (can be overridden by env)
COLLECTION="${COLLECTION:-}"
ENV_FILE="${ENV_FILE:-}"

if [[ -z "${COLLECTION}" ]]; then
  # Default: try to auto-pick a collection json in e2e/
  if compgen -G "$PROJECT_DIR/e2e/*.postman_collection*.json" > /dev/null; then
    COLLECTION="$(ls -1 "$PROJECT_DIR/e2e/"*.postman_collection*.json | tail -n 1)"
    warn "COLLECTION not set; using latest found: $COLLECTION"
  else
    die "COLLECTION not set and no collection found in $PROJECT_DIR/e2e"
  fi
fi

if [[ -z "${ENV_FILE}" ]]; then
  if compgen -G "$PROJECT_DIR/e2e/*.postman_environment*.json" > /dev/null; then
    ENV_FILE="$(ls -1 "$PROJECT_DIR/e2e/"*.postman_environment*.json | tail -n 1)"
    warn "ENV_FILE not set; using latest found: $ENV_FILE"
  else
    die "ENV_FILE not set and no environment found in $PROJECT_DIR/e2e"
  fi
fi

DB_NAME="${DB_NAME:-db_multitenancy}"
PGUSER="${PGUSER:-postgres}"
PGPASSWORD="${PGPASSWORD:-admin}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"

# App start
APP_LOG="${APP_LOG:-$PROJECT_DIR/.e2e-app.log}"
APP_PID_FILE="${APP_PID_FILE:-$PROJECT_DIR/.e2e-app.pid}"

START_TIMEOUT_SEC="${START_TIMEOUT_SEC:-120}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
HEALTH_TIMEOUT_SEC="${HEALTH_TIMEOUT_SEC:-60}"

# Newman guards
NEWMAN_TIMEOUT_SEC="${NEWMAN_TIMEOUT_SEC:-300}"
NEWMAN_LOG="${NEWMAN_LOG:-$PROJECT_DIR/.e2e-newman.log}"

# Effective env file
EFFECTIVE_ENV="${EFFECTIVE_ENV:-$PROJECT_DIR/.env.effective.json}"

cleanup() {
  if [[ -f "$APP_PID_FILE" ]]; then
    local pid
    pid="$(cat "$APP_PID_FILE" 2>/dev/null || true)"
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      info "Stop app (pid=$pid)"
      kill "$pid" 2>/dev/null || true
      sleep 2 || true
      kill -9 "$pid" 2>/dev/null || true
    fi
  fi
}
trap cleanup EXIT

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

require_cmd psql
require_cmd curl
require_cmd newman

# python is used to patch JSON reliably (preferred). If not available, we fall back to jq.
has_python=0
if command -v python >/dev/null 2>&1; then
  has_python=1
elif command -v python3 >/dev/null 2>&1; then
  has_python=1
  alias python=python3
fi

has_jq=0
if command -v jq >/dev/null 2>&1; then
  has_jq=1
fi

db_drop_and_create() {
  info "Drop DB ($DB_NAME)"
  export PGPASSWORD="$PGPASSWORD"
  psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -v ON_ERROR_STOP=1 <<SQL
SELECT pg_terminate_backend(pid)
  FROM pg_stat_activity
 WHERE datname = '${DB_NAME}'
   AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS ${DB_NAME};
CREATE DATABASE ${DB_NAME};
SQL
}

start_app() {
  info "Start app"
  (cd "$PROJECT_DIR" && ./mvnw -q spring-boot:run > "$APP_LOG" 2>&1 & echo $! > "$APP_PID_FILE")
  local pid
  pid="$(cat "$APP_PID_FILE")"
  info "App started (pid=$pid). Logs: $APP_LOG"
}

wait_started() {
  info "Wait for STARTED (timeout=${START_TIMEOUT_SEC}s)"
  local start_ts now elapsed
  start_ts="$(date +%s)"
  while true; do
    if grep -q "Started .*Application" "$APP_LOG" 2>/dev/null; then
      info "App STARTED."
      return 0
    fi
    now="$(date +%s)"
    elapsed=$(( now - start_ts ))
    if (( elapsed > START_TIMEOUT_SEC )); then
      warn "Startup log tail:"
      tail -n 80 "$APP_LOG" || true
      die "App did not start within ${START_TIMEOUT_SEC}s"
    fi
    sleep 1
  done
}

health_check() {
  info "Health check ($HEALTH_URL)"
  local start_ts now elapsed
  start_ts="$(date +%s)"
  while true; do
    if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
      info "Health OK."
      return 0
    fi
    now="$(date +%s)"
    elapsed=$(( now - start_ts ))
    if (( elapsed > HEALTH_TIMEOUT_SEC )); then
      die "Health check failed within ${HEALTH_TIMEOUT_SEC}s"
    fi
    sleep 1
  done
}

# JSON helpers for Postman env format: {"values":[{"key":"x","value":"y","enabled":true},...]}
json_set_env_var_python() {
  local file="$1" key="$2" value="$3"
  python - <<PY
import json
from pathlib import Path
p = Path(r"$file")
obj = json.loads(p.read_text(encoding="utf-8"))
vals = obj.get("values") or []
for item in vals:
    if item.get("key") == "$key":
        item["value"] = "$value"
        item["enabled"] = True
        break
else:
    vals.append({"key":"$key","value":"$value","enabled":True})
obj["values"] = vals
p.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")
PY
}

json_get_env_var_python() {
  local file="$1" key="$2"
  python - <<PY
import json
from pathlib import Path
p = Path(r"$file")
obj = json.loads(p.read_text(encoding="utf-8"))
out = ""
for item in obj.get("values") or []:
    if item.get("key") == "$key":
        v = item.get("value")
        out = "" if v is None else str(v)
        break
print(out)
PY
}

json_set_env_var_jq() {
  local file="$1" key="$2" value="$3"
  local tmp="${file}.tmp"
  jq --arg k "$key" --arg v "$value" '
    if (.values | map(.key) | index($k)) == null then
      .values += [{"key":$k,"value":$v,"enabled":true}]
    else
      .values = (.values | map(if .key==$k then .value=$v | .enabled=true else . end))
    end
  ' "$file" > "$tmp" && mv "$tmp" "$file"
}

json_get_env_var_jq() {
  local file="$1" key="$2"
  jq -r --arg k "$key" '.values[]? | select(.key==$k) | .value // ""' "$file" 2>/dev/null | head -n 1
}

json_set_env_var() {
  local file="$1" key="$2" value="$3"
  if (( has_python == 1 )); then
    json_set_env_var_python "$file" "$key" "$value"
  elif (( has_jq == 1 )); then
    json_set_env_var_jq "$file" "$key" "$value"
  else
    die "Need python (preferred) or jq to patch environment JSON"
  fi
}

json_get_env_var() {
  local file="$1" key="$2"
  if (( has_python == 1 )); then
    json_get_env_var_python "$file" "$key"
  elif (( has_jq == 1 )); then
    json_get_env_var_jq "$file" "$key"
  else
    echo ""
  fi
}

patch_env() {
  info "Patch ENV (effective env for Newman)"
  cp -f "$ENV_FILE" "$EFFECTIVE_ENV"

  # Ensure tenant vars exist. If missing, generate safe defaults.
  local tenant_email tenant_password
  tenant_email="$(json_get_env_var "$EFFECTIVE_ENV" "tenant_email")"
  tenant_password="$(json_get_env_var "$EFFECTIVE_ENV" "tenant_password")"

  if [[ -z "$tenant_email" ]]; then
    tenant_email="e2e_$(date +%s)_$RANDOM@tenant.local"
    warn "tenant_email missing; generated: $tenant_email"
    json_set_env_var "$EFFECTIVE_ENV" "tenant_email" "$tenant_email"
  else
    info "tenant_email = $tenant_email"
  fi

  if [[ -z "$tenant_password" ]]; then
    tenant_password="Admin123!"
    warn "tenant_password missing; using default"
    json_set_env_var "$EFFECTIVE_ENV" "tenant_password" "$tenant_password"
  fi

  # Optional: tax id defaults (only set if missing)
  local tax_type tax_number
  tax_type="$(json_get_env_var "$EFFECTIVE_ENV" "tenant_tax_id_type")"
  tax_number="$(json_get_env_var "$EFFECTIVE_ENV" "tenant_tax_id_number")"
  if [[ -z "$tax_type" ]]; then
    json_set_env_var "$EFFECTIVE_ENV" "tenant_tax_id_type" "CNPJ"
  fi
  if [[ -z "$tax_number" ]]; then
    json_set_env_var "$EFFECTIVE_ENV" "tenant_tax_id_number" "12345678000199"
  fi

  info "effective_env_written = $EFFECTIVE_ENV"
}

run_newman_with_guards() {
  info "Run Newman (timeout=${NEWMAN_TIMEOUT_SEC}s)"
  : > "$NEWMAN_LOG"

  set +e
  newman run "$COLLECTION" -e "$EFFECTIVE_ENV" --insecure --reporters cli \
    | tee -a "$NEWMAN_LOG" &
  local newman_pid=$!
  set -e

  local start_ts now elapsed
  start_ts="$(date +%s)"
  while kill -0 "$newman_pid" 2>/dev/null; do
    # App death guard
    if [[ -f "$APP_PID_FILE" ]]; then
      local app_pid
      app_pid="$(cat "$APP_PID_FILE" 2>/dev/null || true)"
      if [[ -n "${app_pid:-}" ]] && ! kill -0 "$app_pid" 2>/dev/null; then
        warn "App process died while Newman is running. Aborting Newman."
        kill "$newman_pid" 2>/dev/null || true
        break
      fi
    fi

    now="$(date +%s)"
    elapsed=$(( now - start_ts ))
    if (( elapsed > NEWMAN_TIMEOUT_SEC )); then
      warn "Newman timeout reached (${NEWMAN_TIMEOUT_SEC}s). Aborting."
      kill "$newman_pid" 2>/dev/null || true
      break
    fi
    sleep 1
  done

  wait "$newman_pid" || true

  info "Newman finished. Log: $NEWMAN_LOG"

  if grep -Eqi "(AssertionError|error:|ERR!|TypeError|ReferenceError)" "$NEWMAN_LOG"; then
    warn "Newman log contains errors."
    return 1
  fi
  return 0
}

main() {
  info "Runner: run-e2e-reset.sh ($VERSION)"
  info "Collection: $COLLECTION"
  info "Env file:   $ENV_FILE"

  db_drop_and_create
  start_app
  wait_started
  health_check
  patch_env

  if run_newman_with_guards; then
    info "DONE (OK)"
  else
    warn "DONE (FAIL)"
    exit 1
  fi
}

main "$@"
