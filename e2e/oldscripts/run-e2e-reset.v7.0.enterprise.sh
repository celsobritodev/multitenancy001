#!/bin/bash
set -euo pipefail

# ============================================================
# Runner: run-e2e-reset.sh (v10.9.17-ARCH-GUARD)
#
# Improvements:
# - Architectural guardrails after Newman
# - Detect transaction conflicts
# - Detect schema/migration issues
# ============================================================

VERSION="v10.9.17-ARCH-GUARD"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

COLLECTION="${COLLECTION:-}"
ENV_FILE="${ENV_FILE:-}"

resolve_latest_file () {
  local dir="$1"
  local pattern="$2"
  ls -1 "$dir"/$pattern 2>/dev/null | sort -V | tail -n 1
}

if [[ -z "$COLLECTION" ]]; then
  latest="$(resolve_latest_file "$PROJECT_DIR/e2e" "multitenancy001.postman_collection.v*.json")"
  [[ -n "$latest" ]] && COLLECTION="e2e/$(basename "$latest")"
fi

if [[ -z "$ENV_FILE" ]]; then
  latest_env="$(resolve_latest_file "$PROJECT_DIR/e2e" "multitenancy001.local.postman_environment.v*.json")"
  [[ -n "$latest_env" ]] && ENV_FILE="e2e/$(basename "$latest_env")"
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

# ------------------------------------------------------------
# CHECK PORT
# ------------------------------------------------------------

check_port_available() {
  local port="$1"

  banner "Checking if port $port is available"

  if command -v lsof >/dev/null 2>&1; then
    if lsof -i :"$port" >/dev/null 2>&1; then
      die "Port $port already in use"
    fi
  fi

  echo -e "${GREEN}✅ Port $port available${NC}"
}

# ------------------------------------------------------------
# CLEANUP
# ------------------------------------------------------------

cleanup () {
  if [[ -n "${APP_PID:-}" ]]; then
    echo "==> Stop app (pid=$APP_PID)"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

# ------------------------------------------------------------
# DROP DB
# ------------------------------------------------------------

drop_db () {
  banner "Drop DB ($DB_NAME)"

  export PGPASSWORD="$DB_PASSWORD"

  psql -w -X -v ON_ERROR_STOP=1 \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d postgres <<SQL

SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$DB_NAME'
  AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS $DB_NAME;
CREATE DATABASE $DB_NAME;

SQL
}

# ------------------------------------------------------------
# START APP
# ------------------------------------------------------------

start_app () {
  banner "Start app"

  : > "$APP_LOG"

  (cd "$PROJECT_DIR" && ./mvnw -q spring-boot:run >"$APP_LOG" 2>&1) &
  APP_PID="$!"

  echo "App PID=$APP_PID"
}

# ------------------------------------------------------------
# WAIT START
# ------------------------------------------------------------

wait_started () {

  banner "Waiting application start"

  local start_ts
  start_ts="$(date +%s)"

  while true; do

    if grep -q "Started .*Application" "$APP_LOG" 2>/dev/null; then
      echo "Application STARTED"
      return
    fi

    if (( $(date +%s) - start_ts > APP_START_TIMEOUT )); then
      tail -n 200 "$APP_LOG"
      die "Application did not start"
    fi

    sleep 1
  done
}

# ------------------------------------------------------------
# HEALTH
# ------------------------------------------------------------

health_check () {

  local url="http://localhost:${APP_PORT}${APP_HEALTH_PATH}"

  banner "Health check $url"

  for i in {1..30}; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "Health OK"
      return
    fi
    sleep 1
  done

  die "Health check failed"
}

# ------------------------------------------------------------
# PATCH ENV
# ------------------------------------------------------------

patch_env () {

  banner "Prepare effective env"

  local src="$PROJECT_DIR/$ENV_FILE"

  if command -v jq >/dev/null 2>&1; then
    jq '.' "$src" > "$EFFECTIVE_ENV"
  else
    cp "$src" "$EFFECTIVE_ENV"
  fi

  echo "Env ready -> $EFFECTIVE_ENV"
}

# ------------------------------------------------------------
# RUN NEWMAN
# ------------------------------------------------------------

run_newman () {

  banner "Run Newman"

  command -v newman >/dev/null 2>&1 || die "newman not installed"

  : > "$NEWMAN_OUT"

  set +e

  newman run "$PROJECT_DIR/$COLLECTION" \
    -e "$EFFECTIVE_ENV" \
    --timeout-request 30000 \
    --timeout-script 30000 \
    --insecure \
    2>&1 | tee -a "$NEWMAN_OUT"

  local status=${PIPESTATUS[0]}

  set -e

  if [[ $status -ne 0 ]]; then
    echo "Newman failed"
    tail -n 200 "$APP_LOG"
    exit $status
  fi
}

# ------------------------------------------------------------
# ARCHITECTURE CHECK
# ------------------------------------------------------------

check_architecture () {

  banner "Checking architectural errors"

  if grep -E "Pre-bound JDBC|TenantContext.bindTenantSchema|IllegalTransactionStateException|relation .* does not exist|schema .* does not exist" "$APP_LOG" >/dev/null
  then
    echo ""
    echo "==========================================================="
    echo -e "${RED}❌ Architectural error detected${NC}"
    echo "==========================================================="
    echo ""

    grep -E "Pre-bound JDBC|TenantContext.bindTenantSchema|IllegalTransactionStateException|relation .* does not exist|schema .* does not exist" "$APP_LOG" || true

    echo ""
    echo "---- Last 200 lines of log ----"
    tail -n 200 "$APP_LOG" || true

    exit 1
  fi

  echo -e "${GREEN}✔ Arquitetura multi-tenant saudável${NC}"
}

# ------------------------------------------------------------
# MAIN
# ------------------------------------------------------------

main () {

  echo "Runner $VERSION"

  echo "Collection: $COLLECTION"
  echo "Env: $ENV_FILE"

  check_port_available "$APP_PORT"

  drop_db

  start_app

  wait_started

  health_check

  patch_env

  run_newman

  check_architecture

  echo ""
  echo "==========================================================="
  echo -e "${GREEN}✅ SUCCESS ($VERSION)${NC}"
  echo "==========================================================="
  echo ""
}

main "$@"

# =========================================================
# ENTERPRISE MULTI-TENANT TESTS (v6)
# =========================================================

echo "==> Parallel login stress test (10 concurrent)"
seq 1 10 | xargs -I{} -P 10 bash -c '
  curl -s -X POST http://localhost:8080/api/tenant/auth/login     -H "Content-Type: application/json"     -d "{"email":"$E2E_TENANT_EMAIL","password":"$E2E_TENANT_PASSWORD"}"     >/dev/null
'

echo "==> Tenant isolation smoke test"
curl -s http://localhost:8080/api/tenant/me -H "Authorization: Bearer $TENANT1_TOKEN" > /tmp/t1.json
curl -s http://localhost:8080/api/tenant/me -H "Authorization: Bearer $TENANT2_TOKEN" > /tmp/t2.json

echo "Tenant1:"
cat /tmp/t1.json
echo "Tenant2:"
cat /tmp/t2.json



# =========================================================
# ENTERPRISE TEST SUITE (v7)
# =========================================================

echo "==> STRESS TEST: 100 parallel logins"
seq 1 100 | xargs -I{} -P 100 bash -c '
curl -s -X POST http://localhost:8080/api/tenant/auth/login -H "Content-Type: application/json" -d "{"email":"$E2E_TENANT_EMAIL","password":"$E2E_TENANT_PASSWORD"}" >/dev/null
'

echo "==> RACE TEST: concurrent user creation"
seq 1 20 | xargs -I{} -P 20 bash -c '
curl -s -X POST http://localhost:8080/api/tenant/users -H "Authorization: Bearer $TENANT1_TOKEN" -H "X-Tenant: $TENANT1_SCHEMA" -H "Content-Type: application/json" -d "{"name":"RaceUser{}","email":"raceuser{}@tenant.local","password":"E2ePass123","role":"TENANT_USER","permissions":[]}" >/dev/null
'

echo "==> SECURITY TEST: token reuse"
curl -s http://localhost:8080/api/tenant/me -H "Authorization: Bearer $TENANT1_TOKEN" > /tmp/token_test1.json
curl -s http://localhost:8080/api/tenant/me -H "Authorization: Bearer $TENANT1_TOKEN" > /tmp/token_test2.json

echo "==> CROSS TENANT LEAK CHECK"
curl -s http://localhost:8080/api/tenant/me -H "Authorization: Bearer $TENANT1_TOKEN" > /tmp/tenantA.json
curl -s http://localhost:8080/api/tenant/me -H "Authorization: Bearer $TENANT2_TOKEN" > /tmp/tenantB.json

echo "TenantA:"
cat /tmp/tenantA.json
echo "TenantB:"
cat /tmp/tenantB.json

echo "==> Checking architectural errors"

grep -E "Pre-bound JDBC" .e2e-app.log && exit 1
grep -E "TenantContext.bindTenantSchema" .e2e-app.log && exit 1
grep -E "IllegalTransactionStateException" .e2e-app.log && exit 1
grep -E "relation .* does not exist" .e2e-app.log && exit 1
grep -E "schema .* does not exist" .e2e-app.log && exit 1

echo "✔ Arquitetura multi-tenant saudável"
