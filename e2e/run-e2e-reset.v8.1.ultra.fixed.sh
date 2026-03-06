#!/bin/bash
set -euo pipefail

# ============================================================
# Runner: run-e2e-reset.sh (v10.9.17-ARCH-GUARD)
#
# Improvements:
# - Architectural guardrails after Newman
# - Detect transaction conflicts
# - Detect schema/migration issues
# - Safe extra enterprise/ultra tests
# - No unbound variable crashes
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

# Runtime vars carregadas do env efetivo
BASE_URL=""
E2E_TENANT_EMAIL=""
E2E_TENANT_PASSWORD=""
TENANT1_TOKEN=""
TENANT2_TOKEN=""
TENANT1_SCHEMA=""
TENANT2_SCHEMA=""
SUPERADMIN_TOKEN=""

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

  local in_use=0

  if command -v lsof >/dev/null 2>&1; then
    if lsof -i :"$port" >/dev/null 2>&1; then
      in_use=1
    fi
  elif command -v netstat >/dev/null 2>&1; then
    if netstat -ano 2>/dev/null | grep -E "LISTENING|LISTEN" | grep -q ":$port "; then
      in_use=1
    fi
  elif command -v ss >/dev/null 2>&1; then
    if ss -lnt 2>/dev/null | grep -q ":$port "; then
      in_use=1
    fi
  fi

  if [[ "$in_use" -eq 1 ]]; then
    die "Port $port already in use"
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
      tail -n 200 "$APP_LOG" || true
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

  for _ in {1..30}; do
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

  [[ -f "$src" ]] || die "Env file not found: $src"

  if command -v jq >/dev/null 2>&1; then
    jq '.' "$src" > "$EFFECTIVE_ENV"
  else
    cp "$src" "$EFFECTIVE_ENV"
  fi

  echo "Env ready -> $EFFECTIVE_ENV"
}

# ------------------------------------------------------------
# READ EFFECTIVE ENV HELPERS
# ------------------------------------------------------------

read_env_value () {
  local key="$1"

  [[ -f "$EFFECTIVE_ENV" ]] || return 1
  command -v jq >/dev/null 2>&1 || return 1

  jq -r --arg KEY "$key" '.values[]? | select(.key == $KEY) | .value' "$EFFECTIVE_ENV" 2>/dev/null | tail -n 1
}

normalize_nullish () {
  local v="${1:-}"
  if [[ -z "$v" || "$v" == "null" ]]; then
    echo ""
  else
    echo "$v"
  fi
}

load_runtime_vars () {
  BASE_URL="$(normalize_nullish "$(read_env_value "base_url" || true)")"
  E2E_TENANT_EMAIL="$(normalize_nullish "$(read_env_value "tenant_email" || true)")"
  E2E_TENANT_PASSWORD="$(normalize_nullish "$(read_env_value "tenant_password" || true)")"

  TENANT1_TOKEN="$(normalize_nullish "$(read_env_value "tenant_token" || true)")"
  TENANT2_TOKEN="$(normalize_nullish "$(read_env_value "tenant2_token" || true)")"

  TENANT1_SCHEMA="$(normalize_nullish "$(read_env_value "tenant_schema" || true)")"
  TENANT2_SCHEMA="$(normalize_nullish "$(read_env_value "tenant2_schema" || true)")"

  SUPERADMIN_TOKEN="$(normalize_nullish "$(read_env_value "superadmin_token" || true)")"

  if [[ -z "$BASE_URL" ]]; then
    BASE_URL="http://localhost:${APP_PORT}"
  fi
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
    tail -n 200 "$APP_LOG" || true
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
# SAFE EXTRA TEST HELPERS
# ------------------------------------------------------------

require_var_or_skip () {
  local value="$1"
  local label="$2"

  if [[ -z "$value" ]]; then
    echo -e "${YELLOW}⚠ Skipping: missing $label${NC}"
    return 1
  fi

  return 0
}

# ------------------------------------------------------------
# ENTERPRISE MULTI-TENANT TESTS (SAFE)
# ------------------------------------------------------------

parallel_login_stress_10 () {
  banner "Parallel login stress test (10 concurrent)"

  require_var_or_skip "$E2E_TENANT_EMAIL" "tenant_email" || return 0
  require_var_or_skip "$E2E_TENANT_PASSWORD" "tenant_password" || return 0

  seq 1 10 | xargs -I{} -P 10 bash -c '
    curl -s -X POST "'"$BASE_URL"'/api/tenant/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"'"$E2E_TENANT_EMAIL"'\",\"password\":\"'"$E2E_TENANT_PASSWORD"'\"}" \
      >/dev/null
  '

  echo -e "${GREEN}✅ Parallel login stress test finished${NC}"
}

tenant_isolation_smoke_test () {
  banner "Tenant isolation smoke test"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT2_TOKEN" "tenant2_token" || return 0

  local tmp1 tmp2
  tmp1="$PROJECT_DIR/.tenant1.me.json"
  tmp2="$PROJECT_DIR/.tenant2.me.json"

  curl -s "$BASE_URL/api/tenant/me" \
    -H "Authorization: Bearer $TENANT1_TOKEN" > "$tmp1"

  curl -s "$BASE_URL/api/tenant/me" \
    -H "Authorization: Bearer $TENANT2_TOKEN" > "$tmp2"

  echo "Tenant1:"
  cat "$tmp1"
  echo ""
  echo "Tenant2:"
  cat "$tmp2"
  echo ""

  if cmp -s "$tmp1" "$tmp2"; then
    die "Tenant isolation smoke test failed: identical responses"
  fi

  echo -e "${GREEN}✅ Tenant isolation smoke test passed${NC}"
}

stress_test_100_parallel_logins () {
  banner "STRESS TEST: 100 parallel logins"

  require_var_or_skip "$E2E_TENANT_EMAIL" "tenant_email" || return 0
  require_var_or_skip "$E2E_TENANT_PASSWORD" "tenant_password" || return 0

  seq 1 100 | xargs -I{} -P 100 bash -c '
    curl -s -X POST "'"$BASE_URL"'/api/tenant/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"'"$E2E_TENANT_EMAIL"'\",\"password\":\"'"$E2E_TENANT_PASSWORD"'\"}" \
      >/dev/null
  '

  echo -e "${GREEN}✅ 100 parallel logins finished${NC}"
}

race_test_concurrent_user_creation () {
  banner "RACE TEST: concurrent user creation"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0

  seq 1 20 | xargs -I{} -P 20 bash -c '
    curl -s -X POST "'"$BASE_URL"'/api/tenant/users" \
      -H "Authorization: Bearer '"$TENANT1_TOKEN"'" \
      -H "X-Tenant: '"$TENANT1_SCHEMA"'" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"RaceUser{}\",\"email\":\"raceuser{}@tenant.local\",\"password\":\"E2ePass123\",\"role\":\"TENANT_USER\",\"permissions\":[]}" \
      >/dev/null
  '

  echo -e "${GREEN}✅ Concurrent user creation test finished${NC}"
}

security_test_token_reuse () {
  banner "SECURITY TEST: token reuse"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0

  curl -s "$BASE_URL/api/tenant/me" \
    -H "Authorization: Bearer $TENANT1_TOKEN" > "$PROJECT_DIR/.token_test1.json"

  curl -s "$BASE_URL/api/tenant/me" \
    -H "Authorization: Bearer $TENANT1_TOKEN" > "$PROJECT_DIR/.token_test2.json"

  echo -e "${GREEN}✅ Token reuse smoke finished${NC}"
}

cross_tenant_leak_check () {
  banner "CROSS TENANT LEAK CHECK"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT2_TOKEN" "tenant2_token" || return 0

  curl -s "$BASE_URL/api/tenant/me" \
    -H "Authorization: Bearer $TENANT1_TOKEN" > "$PROJECT_DIR/.tenantA.json"

  curl -s "$BASE_URL/api/tenant/me" \
    -H "Authorization: Bearer $TENANT2_TOKEN" > "$PROJECT_DIR/.tenantB.json"

  echo "TenantA:"
  cat "$PROJECT_DIR/.tenantA.json"
  echo ""
  echo "TenantB:"
  cat "$PROJECT_DIR/.tenantB.json"
  echo ""

  echo -e "${GREEN}✅ Cross tenant leak check executed${NC}"
}

# ------------------------------------------------------------
# ULTRA SAAS TEST SUITE (SAFE)
# ------------------------------------------------------------

ultra_stress_sales_1000 () {
  banner "ULTRA STRESS: 1000 simulated sales"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0

  seq 1 1000 | xargs -I{} -P 100 bash -c '
    curl -s -X POST "'"$BASE_URL"'/api/tenant/sales" \
      -H "Authorization: Bearer '"$TENANT1_TOKEN"'" \
      -H "X-Tenant: '"$TENANT1_SCHEMA"'" \
      -H "Content-Type: application/json" \
      -d "{\"saleDate\":\"2026-01-01T00:00:00Z\",\"status\":\"DRAFT\",\"items\":[{\"productId\":\"00000000-0000-0000-0000-000000000001\",\"productName\":\"Stress Product\",\"quantity\":1,\"unitPrice\":100}]}" \
      >/dev/null
  '

  echo -e "${GREEN}✅ 1000 simulated sales finished${NC}"
}

inventory_concurrency_test () {
  banner "INVENTORY CONCURRENCY TEST"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0

  seq 1 50 | xargs -I{} -P 50 bash -c '
    curl -s -X GET "'"$BASE_URL"'/api/tenant/products/low-stock/count?threshold=5" \
      -H "Authorization: Bearer '"$TENANT1_TOKEN"'" \
      -H "X-Tenant: '"$TENANT1_SCHEMA"'" \
      >/dev/null
  '

  echo -e "${GREEN}✅ Inventory concurrency smoke finished${NC}"
}

postgres_deadlock_detector () {
  banner "POSTGRES DEADLOCK DETECTOR"

  if grep -i "deadlock detected" "$APP_LOG" >/dev/null 2>&1; then
    grep -i "deadlock detected" "$APP_LOG" || true
    die "Deadlock detected in app log"
  fi

  echo -e "${GREEN}✅ No deadlock detected${NC}"
}

simulate_50_tenants () {
  banner "SIMULATING 50 TENANTS"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0

  for i in $(seq 1 50); do
    curl -s "$BASE_URL/api/tenant/me" \
      -H "Authorization: Bearer $TENANT1_TOKEN" \
      -H "X-Tenant: tenant_test_$i" >/dev/null || true
  done

  echo -e "${GREEN}✅ 50 tenant simulation finished${NC}"
}

billing_multi_tenant_test () {
  banner "BILLING MULTI-TENANT TEST"

  require_var_or_skip "$SUPERADMIN_TOKEN" "superadmin_token" || return 0

  seq 1 20 | xargs -I{} -P 10 bash -c '
    curl -s -X POST "'"$BASE_URL"'/api/controlplane/billing/payments" \
      -H "Authorization: Bearer '"$SUPERADMIN_TOKEN"'" \
      -H "Content-Type: application/json" \
      -d "{\"accountId\":2,\"amount\":100,\"paymentGateway\":\"MANUAL\",\"paymentMethod\":\"PIX\"}" \
      >/dev/null
  '

  echo -e "${GREEN}✅ Billing multi-tenant test finished${NC}"
}

run_extra_suites () {
  load_runtime_vars

  parallel_login_stress_10
  tenant_isolation_smoke_test

  stress_test_100_parallel_logins
  race_test_concurrent_user_creation
  security_test_token_reuse
  cross_tenant_leak_check

  postgres_deadlock_detector
  inventory_concurrency_test
  simulate_50_tenants
  billing_multi_tenant_test

  echo "ULTRA SUITE COMPLETED"
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

  run_extra_suites
}

main "$@"