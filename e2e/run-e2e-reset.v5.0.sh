#!/bin/bash
set -euo pipefail

# ============================================================
# Runner: run-e2e-reset.sh (v10.9.16-PATCHED4)
#
# Fixes vs PATCHED3:
# - Remove Python dependency in patch_env (Windows/Git Bash path issues)
# - Create effective env using jq (if available) or cp fallback
# - Keep port 8080 availability check + standard flow
# ============================================================

VERSION="v10.9.16-PATCHED4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# =========================================================
# Cores para output
# =========================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# =========================================================
# Inputs (defaults)
# =========================================================
COLLECTION="${COLLECTION:-}"
ENV_FILE="${ENV_FILE:-}"

resolve_latest_file () {
  local dir="$1"
  local pattern="$2"
  ls -1 "$dir"/$pattern 2>/dev/null | sort -V | tail -n 1
}

if [[ -z "$COLLECTION" ]]; then
  latest="$(resolve_latest_file "$PROJECT_DIR/e2e" "multitenancy001.postman_collection.v*.json")"
  if [[ -n "$latest" ]]; then
    COLLECTION="e2e/$(basename "$latest")"
  fi
fi

if [[ -z "$ENV_FILE" ]]; then
  latest_env="$(resolve_latest_file "$PROJECT_DIR/e2e" "multitenancy001.local.postman_environment.v*.json")"
  if [[ -n "$latest_env" ]]; then
    ENV_FILE="e2e/$(basename "$latest_env")"
  fi
fi

if [[ -z "${COLLECTION}" || -z "${ENV_FILE}" ]]; then
  echo "Missing inputs."
  echo 'Usage: COLLECTION="e2e/....json" ENV_FILE="e2e/....json" ./e2e/run-e2e-reset.sh'
  exit 2
fi

APP_PORT="${APP_PORT:-8080}"
APP_HEALTH_PATH="${APP_HEALTH_PATH:-/actuator/health}"
APP_START_TIMEOUT="${APP_START_TIMEOUT:-120}"

DB_NAME="${DB_NAME:-db_multitenancy}"
DB_USER="${DB_USER:-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_PASSWORD="${DB_PASSWORD:-admin}"

APP_LOG="${APP_LOG:-$PROJECT_DIR/.e2e-app.log}"
EFFECTIVE_ENV="${EFFECTIVE_ENV:-$PROJECT_DIR/.env.effective.json}"
NEWMAN_OUT="${NEWMAN_OUT:-$PROJECT_DIR/.e2e-newman.out.log}"

APP_PID=""

banner () { echo "==> $1"; }

die () {
  echo ""
  echo "==========================================================="
  echo -e "${RED}❌ $1${NC}"
  echo "==========================================================="
  echo ""
  exit "${2:-1}"
}

# =========================================================
# Verifica se a porta já está em uso
# =========================================================
check_port_available() {
  local port="$1"
  banner "Verificando se a porta $port está disponível"

  local port_in_use=false
  local check_method=""

  if command -v netstat >/dev/null 2>&1; then
    check_method="netstat"
    if netstat -ano 2>/dev/null | grep -E "LISTENING|LISTEN" | grep -q ":$port "; then
      port_in_use=true
    fi
  elif command -v ss >/dev/null 2>&1; then
    check_method="ss"
    if ss -lnt 2>/dev/null | grep -q ":$port "; then
      port_in_use=true
    fi
  elif command -v lsof >/dev/null 2>&1; then
    check_method="lsof"
    if lsof -i :"$port" >/dev/null 2>&1; then
      port_in_use=true
    fi
  elif command -v nc >/dev/null 2>&1; then
    check_method="nc"
    if nc -z localhost "$port" 2>/dev/null; then
      port_in_use=true
    fi
  else
    echo -e "${YELLOW}⚠️  Não foi possível verificar se a porta $port está em uso (nenhuma ferramenta encontrada). Continuando...${NC}"
    return 0
  fi

  if $port_in_use; then
    echo -e "${RED}❌ Porta $port já está em uso! (verificado com: $check_method)${NC}"
    echo -e "${YELLOW}   Encerre o processo que está usando a porta antes de executar os testes E2E.${NC}"
    echo ""
    echo "   Para identificar o processo:"
    echo "     - netstat: netstat -ano | grep :$port"
    echo "     - lsof:    lsof -i :$port"
    echo "     - ss:      ss -lnt | grep :$port"
    die "Porta $port em uso"
  else
    echo -e "${GREEN}✅ Porta $port disponível. (verificado com: $check_method)${NC}"
  fi
}

cleanup () {
  if [[ -n "${APP_PID:-}" ]]; then
    echo "==========================================================="
    echo "==> Stop app (pid=$APP_PID)"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

drop_db () {
  banner "Drop DB ($DB_NAME)"
  export PGPASSWORD="$DB_PASSWORD"

  psql -w -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$DB_NAME'
  AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS $DB_NAME;
CREATE DATABASE $DB_NAME;
SQL
}

start_app () {
  banner "Start app"
  : > "$APP_LOG"
  (cd "$PROJECT_DIR" && ./mvnw -q spring-boot:run >"$APP_LOG" 2>&1) &
  APP_PID="$!"
  echo "==> App started (pid=$APP_PID). Logs: $APP_LOG"
}

wait_started () {
  banner "Wait for STARTED (timeout=${APP_START_TIMEOUT}s)"
  local start_ts
  start_ts="$(date +%s)"
  while true; do
    if grep -q "Started .*Application" "$APP_LOG" 2>/dev/null; then
      echo "==> App STARTED."
      return 0
    fi
    local now
    now="$(date +%s)"
    if (( now - start_ts > APP_START_TIMEOUT )); then
      echo ""
      echo "---- Last 200 lines of app log ----"
      tail -n 200 "$APP_LOG" || true
      die "App did not start within ${APP_START_TIMEOUT}s"
    fi
    sleep 1
  done
}

health_check () {
  local url="http://localhost:${APP_PORT}${APP_HEALTH_PATH}"
  banner "Health check ($url)"
  for i in {1..30}; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "==> Health OK."
      return 0
    fi
    sleep 1
  done
  die "Health check failed: $url"
}

patch_env () {
  banner "Patch ENV (effective env for Newman)"
  local src="$PROJECT_DIR/$ENV_FILE"

  if [[ ! -f "$src" ]]; then
    die "Env file not found: $src"
  fi

  # Create a copy; do not mutate original env file
  if command -v jq >/dev/null 2>&1; then
    jq '.' "$src" > "$EFFECTIVE_ENV"
  else
    cp "$src" "$EFFECTIVE_ENV"
  fi

  # Quick peek (safe even without jq)
  if command -v jq >/dev/null 2>&1; then
    echo "tenant_email = $(jq -r '.values[]? | select(.key=="tenant_email") | .value' "$EFFECTIVE_ENV" 2>/dev/null || echo "?")"
    echo "tenant_password = ***"
    echo "tenant_tax_id_type = $(jq -r '.values[]? | select(.key=="tenant_tax_id_type") | .value' "$EFFECTIVE_ENV" 2>/dev/null || echo "?")"
    echo "tenant_tax_id_number = $(jq -r '.values[]? | select(.key=="tenant_tax_id_number") | .value' "$EFFECTIVE_ENV" 2>/dev/null || echo "?")"
    echo "controlplane_email = $(jq -r '.values[]? | select(.key=="controlplane_email") | .value' "$EFFECTIVE_ENV" 2>/dev/null || echo "?")"
    echo "controlplane_password = ***"
  fi
  echo "effective_env_written = $EFFECTIVE_ENV"
}

run_newman () {
  banner "Run Newman"
  command -v newman >/dev/null 2>&1 || die "newman nao encontrado. Instale: npm i -g newman" 127

  : > "$NEWMAN_OUT"

  set +e
  newman run "$PROJECT_DIR/$COLLECTION" -e "$EFFECTIVE_ENV" --timeout-request 30000 --timeout-script 30000 --insecure 2>&1 | tee -a "$NEWMAN_OUT"
  local status=${PIPESTATUS[0]}
  set -e

  if [[ $status -ne 0 ]]; then
    echo ""
    echo "==========================================================="
    echo -e "${RED}❌ Newman FAILED (exit=$status)${NC}"
    echo "   newman log: $NEWMAN_OUT"
    echo "   app log:    $APP_LOG"
    echo "---- Last 200 lines of app log ----"
    tail -n 200 "$APP_LOG" || true
    echo "==========================================================="
    echo ""
    return $status
  fi

  return 0
}

main () {
  echo "==> Runner: run-e2e-reset.sh ($VERSION)"
  echo "==> Collection: $COLLECTION"
  echo "==> Env file:   $ENV_FILE"

  check_port_available "$APP_PORT"
  drop_db
  start_app
  wait_started
  health_check
  patch_env

  if run_newman; then
    echo ""
    echo "==========================================================="
    echo -e "${GREEN}✅ SUCCESS ($VERSION)${NC}"
    echo "==========================================================="
    echo ""
  fi
}

main "$@"
