#!/bin/bash
set -euo pipefail

# ============================================================
# Runner: run-e2e-reset.sh (v10.9.14)
#
# Goal:
# - Drop DB, start app, wait health, patch env, run Newman
# - Abort automatically on detected LOOP (repeated next request)
# - Abort automatically on "product creation failed => sales will loop"
#   WITHOUT changing the Postman collection.
# - Verifica se a porta 8080 já está em uso antes de iniciar
#
# Tunables (env):
#   MAX_LOOP_REPEAT=20            # consecutive "Attempting to set next request to X"
#   LOOP_GUARD_ENABLE=1           # 0 disables loop guard
#   MAX_9904_400=3                # consecutive 99.04 400 (product create) before abort
#   ABORT_ON_NO_PRODUCTS=1        # abort if "Nenhum produto encontrado" appears
#   NO_PRODUCTS_MAX=2             # consecutive "Nenhum produto encontrado" before abort
#   APP_PORT=8080
#   APP_HEALTH_PATH=/actuator/health
#   APP_START_TIMEOUT=120
# ============================================================

VERSION="v10.9.14"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# =========================================================
# Cores para output
# =========================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# =========================================================
# Inputs (defaults)
# - You can still override via:
#   COLLECTION="e2e/....json" ENV_FILE="e2e/....json" ./e2e/run-e2e-reset.sh
# =========================================================
COLLECTION="${COLLECTION:-}"
ENV_FILE="${ENV_FILE:-}"

resolve_latest_file () {
  local dir="$1"
  local pattern="$2"
  # shellcheck disable=SC2010
  ls -1 "$dir"/$pattern 2>/dev/null | sort -V | tail -n 1
}

if [[ -z "$COLLECTION" ]]; then
  # Prefer the pinned v10.9.14 collection if present; otherwise pick the latest by version.
  if [[ -f "$PROJECT_DIR/e2e/multitenancy001.postman_collection.v10.9.14-full.ALL-ENDPOINTS.STRICT+tenant-ambiguity.diagnostics.no5xx.hint-login-identities+mass-flow.deterministic.no-loop.json" ]]; then
    COLLECTION="e2e/multitenancy001.postman_collection.v10.9.14-full.ALL-ENDPOINTS.STRICT+tenant-ambiguity.diagnostics.no5xx.hint-login-identities+mass-flow.deterministic.no-loop.json"
  else
    latest="$(resolve_latest_file "$PROJECT_DIR/e2e" "multitenancy001.postman_collection.v*.json")"
    if [[ -n "$latest" ]]; then
      COLLECTION="e2e/$(basename "$latest")"
    fi
  fi
fi

if [[ -z "$ENV_FILE" ]]; then
  # Prefer v5.1.PATCHED if present; otherwise pick the latest by version.
  if [[ -f "$PROJECT_DIR/e2e/multitenancy001.local.postman_environment.v5.1.PATCHED.json" ]]; then
    ENV_FILE="e2e/multitenancy001.local.postman_environment.v5.1.PATCHED.json"
  else
    latest_env="$(resolve_latest_file "$PROJECT_DIR/e2e" "multitenancy001.local.postman_environment.v*.json")"
    if [[ -n "$latest_env" ]]; then
      ENV_FILE="e2e/$(basename "$latest_env")"
    fi
  fi
fi

if [[ -z "$COLLECTION" || -z "$ENV_FILE" ]]; then
  echo "Missing inputs."
  echo "Usage (override manually if needed):"
  echo '  COLLECTION="e2e/....json" ENV_FILE="e2e/....json" ./e2e/run-e2e-reset.sh'
  echo ""
  echo "Auto-detection failed. Ensure these files exist under: $PROJECT_DIR/e2e/"
  exit 2
fi

MAX_LOOP_REPEAT="${MAX_LOOP_REPEAT:-20}"
LOOP_GUARD_ENABLE="${LOOP_GUARD_ENABLE:-1}"

MAX_9904_400="${MAX_9904_400:-3}"
ABORT_ON_NO_PRODUCTS="${ABORT_ON_NO_PRODUCTS:-1}"
NO_PRODUCTS_MAX="${NO_PRODUCTS_MAX:-2}"

APP_PORT="${APP_PORT:-8080}"
APP_HEALTH_PATH="${APP_HEALTH_PATH:-/actuator/health}"
APP_START_TIMEOUT="${APP_START_TIMEOUT:-120}"

DB_NAME="${DB_NAME:-db_multitenancy}"
DB_USER="${DB_USER:-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_PASSWORD="${DB_PASSWORD:-admin}"

APP_LOG="${APP_LOG:-.e2e-app.log}"
EFFECTIVE_ENV="${EFFECTIVE_ENV:-.env.effective.json}"
NEWMAN_LOG="${NEWMAN_LOG:-.e2e-newman.log}"

APP_PID=""

banner () {
  echo "==> $1"
}

die () {
  echo ""
  echo "==========================================================="
  echo -e "${RED}❌ $1${NC}"
  echo "==========================================================="
  echo ""
  exit "${2:-1}"
}

# =========================================================
# NOVA FUNÇÃO: Verifica se a porta já está em uso (com cores)
# =========================================================
check_port_available() {
  local port="$1"
  banner "Verificando se a porta $port está disponível"
  
  local port_in_use=false
  local check_method=""

  # Tenta usar netstat (disponível no Git Bash/Windows)
  if command -v netstat >/dev/null 2>&1; then
    check_method="netstat"
    if netstat -ano 2>/dev/null | grep -E "LISTENING|LISTEN" | grep -q ":$port "; then
      port_in_use=true
    fi
  # Fallback: tenta usar ss (Linux)
  elif command -v ss >/dev/null 2>&1; then
    check_method="ss"
    if ss -lnt 2>/dev/null | grep -q ":$port "; then
      port_in_use=true
    fi
  # Fallback: tenta usar lsof (macOS/Linux)
  elif command -v lsof >/dev/null 2>&1; then
    check_method="lsof"
    if lsof -i :"$port" >/dev/null 2>&1; then
      port_in_use=true
    fi
  # Último recurso: tenta fazer uma conexão rápida com nc
  elif command -v nc >/dev/null 2>&1; then
    check_method="nc"
    if nc -z localhost "$port" 2>/dev/null; then
      port_in_use=true
    fi
  else
    echo -e "${YELLOW}⚠️  Não foi possível verificar se a porta $port está em uso (nenhuma ferramenta encontrada). Continuando mesmo assim...${NC}"
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
    return 0
  fi
}

cleanup () {
  if [[ -n "${APP_PID:-}" ]]; then
    echo "==========================================================="
    echo "==> Stopping app (pid=$APP_PID)..."
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

drop_db () {
  banner "Drop DB ($DB_NAME)"
  export PGPASSWORD="$DB_PASSWORD"

  # Use maintenance DB "postgres" to drop/create target DB.
  # -X avoids ~/.psqlrc side effects; ON_ERROR_STOP makes psql fail fast.
  psql -w -X -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$DB_NAME'
  AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS $DB_NAME;
CREATE DATABASE $DB_NAME;
SQL

  # Keep PGPASSWORD in env for any later psql diagnostics (if used)
}

start_app () {
  banner "Start app"
  : > "$APP_LOG"
  (cd "$PROJECT_DIR" && ./mvnw -q spring-boot:run >"$APP_LOG" 2>&1) &
  APP_PID="$!"
  echo "==> App started (pid=$APP_PID). Logs: $APP_LOG"
}

wait_started () {
  banner "Wait for STARTED"
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
  banner "Health check"
  local url="http://localhost:${APP_PORT}${APP_HEALTH_PATH}"
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
  # Uses jq if available. Falls back to copying.
  if command -v jq >/dev/null 2>&1; then
    # create a copy; do not mutate original env file
    jq '.' "$ENV_FILE" > "$EFFECTIVE_ENV"
  else
    cp "$ENV_FILE" "$EFFECTIVE_ENV"
  fi

  # Display key presence quick check
  echo "tenant_email = $(jq -r '.values[]? | select(.key=="tenant_email") | .value' "$EFFECTIVE_ENV" 2>/dev/null || echo "?")"
  echo "tenant_password = ***"
  echo "tenant_tax_id_type = $(jq -r '.values[]? | select(.key=="tenant_tax_id_type") | .value' "$EFFECTIVE_ENV" 2>/dev/null || echo "?")"
  echo "tenant_tax_id_number = $(jq -r '.values[]? | select(.key=="tenant_tax_id_number") | .value' "$EFFECTIVE_ENV" 2>/dev/null || echo "?")"
  echo "controlplane_email = $(jq -r '.values[]? | select(.key=="controlplane_email") | .value' "$EFFECTIVE_ENV" 2>/dev/null || echo "?")"
  echo "controlplane_password = ***"
  echo "effective_env_written = $EFFECTIVE_ENV"
  echo "==> Conteudo do arquivo efetivo gerado:"
  grep -n '"key": "controlplane_email"' -n "$EFFECTIVE_ENV" | head -n 1 | sed 's/^.*:/"key": "controlplane_email",/' || true
  grep -n '"key": "controlplane_password"' -n "$EFFECTIVE_ENV" | head -n 1 | sed 's/^.*:/"key": "controlplane_password",/' || true
}

abort_newman () {
  local reason="$1"
  local details="$2"
  echo ""
  echo "==========================================================="
  echo -e "${RED}❌ ABORTING NEWMAN ($VERSION)${NC}"
  echo "   reason: $reason"
  if [[ -n "$details" ]]; then
    echo "   $details"
  fi
  echo ""
  echo "   Notes:"
  echo "     - MAX_LOOP_REPEAT=$MAX_LOOP_REPEAT"
  echo "     - MAX_9904_400=$MAX_9904_400"
  echo "     - ABORT_ON_NO_PRODUCTS=$ABORT_ON_NO_PRODUCTS"
  echo "==========================================================="
  echo ""
}

run_newman_with_guards () {
  echo "==> Run Newman"
  command -v newman >/dev/null 2>&1 || { echo "[ERROR] newman nao encontrado. Instale: npm i -g newman"; return 127; }

  local newman_args=(run "$COLLECTION" -e "$EFFECTIVE_ENV" --timeout-request 30000 --timeout-script 30000 --insecure)

  # Portavel (Git Bash / Linux): evita coproc (pode falhar no MINGW) e evita "set -u" com arrays nao inicializados.
  local newman_out=".e2e-newman.out.log"
  : > "$newman_out"

  set +e
  newman "${newman_args[@]}" 2>&1 | tee -a "$newman_out"
  local newman_status=${PIPESTATUS[0]}
  set -e

  if [[ $newman_status -ne 0 ]]; then
    echo "[ERROR] Newman falhou (exit=$newman_status). Veja: $newman_out"
    return $newman_status
  fi

  return 0
}

show_last_logs_on_fail () {
  echo ""
  echo "==========================================================="
  echo -e "${RED}❌ Newman FAILED. Showing last 200 lines of app log:${NC}"
  echo "   $APP_LOG"
  echo "==========================================================="
  tail -n 200 "$APP_LOG" || true
  echo ""
}

main () {
  echo "==> Runner: run-e2e-reset.sh ($VERSION)"

  # =========================================================
  # NOVA CHAMADA: Verifica porta antes de começar
  # =========================================================
  check_port_available "$APP_PORT"

  drop_db
  start_app
  wait_started
  health_check
  patch_env

  if run_newman_with_guards; then
    echo ""
    echo "==========================================================="
    echo -e "${GREEN}✅ SUCCESS ($VERSION)${NC}"
    echo "==========================================================="
    echo ""
    exit 0
  else
    show_last_logs_on_fail
    exit 1
  fi
}

main "$@"