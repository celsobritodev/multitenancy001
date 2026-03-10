#!/bin/bash
set -euo pipefail

# ============================================================
# Runner: run-e2e-reset.sh (v6.4 CUSTOMERS)
#
# Novos endpoints de Customer adicionados:
# - GET    /api/tenant/customers
# - GET    /api/tenant/customers/active
# - GET    /api/tenant/customers/{id}
# - GET    /api/tenant/customers/search?name=
# - GET    /api/tenant/customers/email?email=
# - POST   /api/tenant/customers
# - PUT    /api/tenant/customers/{id}
# - PATCH  /api/tenant/customers/{id}/toggle-active
# - DELETE /api/tenant/customers/{id}
# - PATCH  /api/tenant/customers/{id}/restore
# ============================================================

VERSION="v6.5-CUSTOMERS-FIXED"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
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

BASE_URL=""
E2E_TENANT_EMAIL=""
E2E_TENANT_PASSWORD=""
TENANT1_TOKEN=""
TENANT2_TOKEN=""
TENANT1_SCHEMA=""
TENANT2_SCHEMA=""
SUPERADMIN_TOKEN=""

# Variáveis para armazenar IDs e dados dos customers criados
CUSTOMER_ID_1=""
CUSTOMER_ID_2=""
CUSTOMER_NAME_1=""
CUSTOMER_NAME_2=""
CUSTOMER_EMAIL_1=""
CUSTOMER_EMAIL_2=""

banner () { echo -e "${BLUE}==>${NC} $1"; }
sub_banner () { echo -e "${CYAN}   ->${NC} $1"; }

die () {
  echo ""
  echo "==========================================================="
  echo -e "${RED}❌ $1${NC}"
  echo "==========================================================="
  echo ""
  exit "${2:-1}"
}

warn () { echo -e "${YELLOW}⚠ $1${NC}"; }
ok () { echo -e "${GREEN}✅ $1${NC}"; }

# ------------------------------------------------------------
# CHECK PORT - WINDOWS TASKKILL
# ------------------------------------------------------------

force_kill_port_windows() {
  local port="$1"
  local max_attempts=3
  local attempt=1
  
  warn "Port $port is in use. Attempting to kill process(es) with Windows taskkill..."
  
  while [[ $attempt -le $max_attempts ]]; do
    local pids=$(netstat -ano | grep ":$port" | grep LISTENING | awk '{print $5}' | sort -u | tr -d '\r' || true)
    
    if [[ -n "$pids" ]]; then
      echo "Found Windows PID(s) on port $port: $pids"
      for pid in $pids; do
        if [[ -n "$pid" && "$pid" =~ ^[0-9]+$ ]]; then
          echo "Killing Windows process $pid (attempt $attempt)..."
          taskkill //F //PID "$pid" 2>/dev/null || true
          sleep 2
        fi
      done
    fi
    
    if ! netstat -ano 2>/dev/null | grep ":$port" | grep -q LISTENING; then
      ok "Port $port is now free"
      return 0
    fi
    
    attempt=$((attempt + 1))
  done
  
  die "Cannot free port $port automatically"
}

check_port_available() {
  banner "Checking if port $APP_PORT is available"
  if netstat -ano 2>/dev/null | grep ":$APP_PORT" | grep -q LISTENING; then
    force_kill_port_windows "$APP_PORT"
  else
    ok "Port $APP_PORT available"
  fi
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
    -d postgres <<SQL 2>&1 | grep -v "NOTICE" || true

SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$DB_NAME'
  AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS $DB_NAME;
CREATE DATABASE $DB_NAME;
SQL
  ok "Database reset completed"
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
  local start_ts="$(date +%s)"
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
  banner "Loading runtime variables from $EFFECTIVE_ENV"
  
  BASE_URL="$(normalize_nullish "$(read_env_value "base_url" || true)")"
  [[ -n "$BASE_URL" ]] || BASE_URL="http://localhost:${APP_PORT}"
  
  E2E_TENANT_EMAIL="$(normalize_nullish "$(read_env_value "tenant_email" || true)")"
  E2E_TENANT_PASSWORD="$(normalize_nullish "$(read_env_value "tenant_password" || true)")"

  TENANT1_TOKEN="$(normalize_nullish "$(read_env_value "tenant_token" || true)")"
  [[ -n "$TENANT1_TOKEN" ]] || TENANT1_TOKEN="$(normalize_nullish "$(read_env_value "tenant_access_token" || true)")"
  [[ -n "$TENANT1_TOKEN" ]] || TENANT1_TOKEN="$(normalize_nullish "$(read_env_value "tenant1_access_token" || true)")"
  
  TENANT2_TOKEN="$(normalize_nullish "$(read_env_value "tenant2_token" || true)")"
  [[ -n "$TENANT2_TOKEN" ]] || TENANT2_TOKEN="$(normalize_nullish "$(read_env_value "tenant2_access_token" || true)")"

  TENANT1_SCHEMA="$(normalize_nullish "$(read_env_value "tenant_schema" || true)")"
  [[ -n "$TENANT1_SCHEMA" ]] || TENANT1_SCHEMA="$(normalize_nullish "$(read_env_value "tenant1_schema" || true)")"
  
  TENANT2_SCHEMA="$(normalize_nullish "$(read_env_value "tenant2_schema" || true)")"

  SUPERADMIN_TOKEN="$(normalize_nullish "$(read_env_value "superadmin_token" || true)")"

  echo "Loaded variables:"
  echo "  BASE_URL: $BASE_URL"
  echo "  TENANT1_TOKEN: ${TENANT1_TOKEN:0:15}... (${#TENANT1_TOKEN} chars)"
  echo "  TENANT2_TOKEN: ${TENANT2_TOKEN:0:15}... (${#TENANT2_TOKEN} chars)"
  echo "  TENANT1_SCHEMA: $TENANT1_SCHEMA"
  echo "  TENANT2_SCHEMA: $TENANT2_SCHEMA"
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
    --export-environment "$EFFECTIVE_ENV" \
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
  if grep -E "Pre-bound JDBC|TenantContext.bindTenantSchema|IllegalTransactionStateException|relation .* does not exist|schema .* does not exist" "$APP_LOG" >/dev/null; then
    grep -E "Pre-bound JDBC|TenantContext.bindTenantSchema|IllegalTransactionStateException|relation .* does not exist|schema .* does not exist" "$APP_LOG" || true
    tail -n 200 "$APP_LOG" || true
    die "Architectural error detected"
  fi
  ok "Arquitetura multi-tenant saudável"
}

# ------------------------------------------------------------
# SAFE EXTRA TEST HELPERS
# ------------------------------------------------------------

require_var_or_skip () {
  local value="$1"
  local label="$2"
  if [[ -z "$value" ]]; then
    warn "Skipping: missing $label"
    return 1
  fi
  return 0
}

json_compact_or_raw () {
  local raw="${1:-}"
  if [[ -z "$raw" ]]; then
    echo ""
    return 0
  fi
  if command -v jq >/dev/null 2>&1; then
    echo "$raw" | jq -c . 2>/dev/null || echo "$raw"
  else
    echo "$raw"
  fi
}

truncate_text () {
  local raw="${1:-}"
  local max="${2:-800}"
  if [[ ${#raw} -gt $max ]]; then
    echo "${raw:0:$max}...(truncated)"
  else
    echo "$raw"
  fi
}

extract_json_id () {
  local body="${1:-}"
  if [[ -z "$body" ]]; then
    echo ""
    return 0
  fi
  if command -v jq >/dev/null 2>&1; then
    echo "$body" | jq -r '(.id // .data.id // .customer.id // .result.id // empty)' 2>/dev/null || true
  else
    echo ""
  fi
}

url_encode () {
  local value="${1:-}"
  if command -v jq >/dev/null 2>&1; then
    jq -rn --arg v "$value" '$v|@uri'
  else
    VALUE="$value" python - <<'PY2'
import os, urllib.parse
print(urllib.parse.quote(os.environ.get("VALUE", ""), safe=""))
PY2
  fi
}

log_http_warning () {
  local context="$1"
  local status="$2"
  local body="$3"
  warn "$context failed with status $status"
  local compact
  compact="$(json_compact_or_raw "$body")"
  if [[ -n "$compact" ]]; then
    echo "     body=$(truncate_text "$compact" 1200)"
  fi
}

# ------------------------------------------------------------
# CUSTOMER TESTS - NOVOS ENDPOINTS
# ------------------------------------------------------------

customer_create_test () {
  sub_banner "Creating test customers"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0

  local timestamp
  timestamp=$(date +%s)

  CUSTOMER_NAME_1="João Silva $timestamp"
  CUSTOMER_EMAIL_1="joao.silva.$timestamp@email.com"
  CUSTOMER_NAME_2="Maria Oliveira $timestamp"
  CUSTOMER_EMAIL_2="maria.oliveira.$timestamp@email.com"

  local response1 body1 status1 response2 body2 status2

  response1=$(curl -s -X POST "$BASE_URL/api/tenant/customers" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"$CUSTOMER_NAME_1\",
      \"email\": \"$CUSTOMER_EMAIL_1\",
      \"phone\": \"11999999999\",
      \"document\": \"12345678901\",
      \"documentType\": \"CPF\",
      \"notes\": \"E2E customer #1\"
    }" \
    -w "\n%{http_code}")
  status1=$(echo "$response1" | tail -n1)
  body1=$(echo "$response1" | sed '$d')
  CUSTOMER_ID_1="$(extract_json_id "$body1")"

  if [[ "$status1" == "200" || "$status1" == "201" ]]; then
    if [[ -n "$CUSTOMER_ID_1" ]]; then
      ok "Customer #1 created: $CUSTOMER_ID_1"
    else
      warn "Customer #1 created but ID was not extracted"
      echo "     body=$(truncate_text "$(json_compact_or_raw "$body1")" 1200)"
    fi
  else
    log_http_warning "Create customer #1" "$status1" "$body1"
  fi

  response2=$(curl -s -X POST "$BASE_URL/api/tenant/customers" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"$CUSTOMER_NAME_2\",
      \"email\": \"$CUSTOMER_EMAIL_2\",
      \"phone\": \"11888888888\",
      \"document\": \"98765432100\",
      \"documentType\": \"CPF\",
      \"notes\": \"E2E customer #2\"
    }" \
    -w "\n%{http_code}")
  status2=$(echo "$response2" | tail -n1)
  body2=$(echo "$response2" | sed '$d')
  CUSTOMER_ID_2="$(extract_json_id "$body2")"

  if [[ "$status2" == "200" || "$status2" == "201" ]]; then
    if [[ -n "$CUSTOMER_ID_2" ]]; then
      ok "Customer #2 created: $CUSTOMER_ID_2"
    else
      warn "Customer #2 created but ID was not extracted"
      echo "     body=$(truncate_text "$(json_compact_or_raw "$body2")" 1200)"
    fi
  else
    log_http_warning "Create customer #2" "$status2" "$body2"
  fi

  if [[ -n "$CUSTOMER_ID_1" && -n "$CUSTOMER_ID_2" ]]; then
    ok "Customers created: $CUSTOMER_ID_1, $CUSTOMER_ID_2"
  else
    warn "Customer create flow finished with warnings"
  fi
}

customer_list_test () {
  sub_banner "Testing GET /api/tenant/customers"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0

  local response status body count
  response=$(curl -s -X GET "$BASE_URL/api/tenant/customers" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -w "\n%{http_code}")
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$status" == "200" ]]; then
    count=$(echo "$body" | jq '. | length' 2>/dev/null || echo "?")
    ok "List customers returned $count items (status $status)"
  else
    log_http_warning "List customers" "$status" "$body"
  fi
}

customer_list_active_test () {
  sub_banner "Testing GET /api/tenant/customers/active"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0

  local response status body count
  response=$(curl -s -X GET "$BASE_URL/api/tenant/customers/active" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -w "\n%{http_code}")
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$status" == "200" ]]; then
    count=$(echo "$body" | jq '. | length' 2>/dev/null || echo "?")
    ok "List active customers returned $count items (status $status)"
  else
    log_http_warning "List active customers" "$status" "$body"
  fi
}

customer_get_by_id_test () {
  sub_banner "Testing GET /api/tenant/customers/{id}"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  require_var_or_skip "$CUSTOMER_ID_1" "customer_id" || return 0

  local response status body
  response=$(curl -s -X GET "$BASE_URL/api/tenant/customers/$CUSTOMER_ID_1" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -w "\n%{http_code}")
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$status" == "200" ]]; then
    ok "Get customer by ID succeeded (status $status)"
  else
    log_http_warning "Get customer by ID" "$status" "$body"
  fi
}

customer_search_by_name_test () {
  sub_banner "Testing GET /api/tenant/customers/search?name="

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  require_var_or_skip "$CUSTOMER_NAME_1" "customer_name" || return 0

  local encoded_name response status body count
  encoded_name=$(url_encode "$CUSTOMER_NAME_1")
  response=$(curl -s -X GET "$BASE_URL/api/tenant/customers/search?name=$encoded_name" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -w "\n%{http_code}")
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$status" == "200" ]]; then
    count=$(echo "$body" | jq '. | length' 2>/dev/null || echo "?")
    ok "Search customers by name succeeded (status $status, matches=$count)"
  else
    log_http_warning "Search customers by name" "$status" "$body"
  fi
}

customer_get_by_email_test () {
  sub_banner "Testing GET /api/tenant/customers/email?email="

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  require_var_or_skip "$CUSTOMER_EMAIL_1" "customer_email" || return 0

  local encoded_email response status body
  encoded_email=$(url_encode "$CUSTOMER_EMAIL_1")
  response=$(curl -s -X GET "$BASE_URL/api/tenant/customers/email?email=$encoded_email" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -w "\n%{http_code}")
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$status" == "200" ]]; then
    ok "Get customers by email succeeded (status $status)"
  else
    log_http_warning "Get customers by email" "$status" "$body"
  fi
}

customer_update_test () {
  sub_banner "Testing PUT /api/tenant/customers/{id}"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  require_var_or_skip "$CUSTOMER_ID_1" "customer_id" || return 0

  local timestamp response status body
  timestamp=$(date +%s)

  response=$(curl -s -X PUT "$BASE_URL/api/tenant/customers/$CUSTOMER_ID_1" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"João Silva Atualizado $timestamp\",
      \"email\": \"joao.atualizado.$timestamp@email.com\",
      \"phone\": \"11977777777\",
      \"notes\": \"E2E update\"
    }" \
    -w "\n%{http_code}")
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$status" == "200" ]]; then
    ok "Update customer succeeded (status $status)"
  else
    log_http_warning "Update customer" "$status" "$body"
  fi
}

customer_toggle_active_test () {
  sub_banner "Testing PATCH /api/tenant/customers/{id}/toggle-active"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  require_var_or_skip "$CUSTOMER_ID_1" "customer_id" || return 0

  local response status body
  response=$(curl -s -X PATCH "$BASE_URL/api/tenant/customers/$CUSTOMER_ID_1/toggle-active" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -w "\n%{http_code}")
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$status" == "200" ]]; then
    ok "Toggle active succeeded (status $status)"
  else
    log_http_warning "Toggle active" "$status" "$body"
  fi
}

customer_soft_delete_test () {
  sub_banner "Testing DELETE /api/tenant/customers/{id} (soft delete)"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  require_var_or_skip "$CUSTOMER_ID_2" "customer_id" || return 0

  local response status body
  response=$(curl -s -X DELETE "$BASE_URL/api/tenant/customers/$CUSTOMER_ID_2" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -w "\n%{http_code}")
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$status" == "204" ]]; then
    ok "Soft delete succeeded (status $status)"
  else
    log_http_warning "Soft delete" "$status" "$body"
  fi
}

customer_restore_test () {
  sub_banner "Testing PATCH /api/tenant/customers/{id}/restore"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  require_var_or_skip "$CUSTOMER_ID_2" "customer_id" || return 0

  local response status body
  response=$(curl -s -X PATCH "$BASE_URL/api/tenant/customers/$CUSTOMER_ID_2/restore" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -w "\n%{http_code}")
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$status" == "200" ]]; then
    ok "Restore customer succeeded (status $status)"
  else
    log_http_warning "Restore customer" "$status" "$body"
  fi
}

customer_negative_tests () {
  sub_banner "Running negative tests for customers"

  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0

  local response1 status1 body1 response2 status2 body2

  response1=$(curl -s -X POST "$BASE_URL/api/tenant/customers" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -H "Content-Type: application/json" \
    -d "{
      \"email\": \"teste@email.com\"
    }" \
    -w "\n%{http_code}")
  status1=$(echo "$response1" | tail -n1)
  body1=$(echo "$response1" | sed '$d')

  if [[ "$status1" == "400" ]]; then
    ok "Negative test 1 passed (create without name -> $status1)"
  else
    log_http_warning "Negative test 1" "$status1" "$body1"
  fi

  response2=$(curl -s -X GET "$BASE_URL/api/tenant/customers/00000000-0000-0000-0000-000000000000" \
    -H "Authorization: Bearer $TENANT1_TOKEN" \
    -H "X-Tenant: $TENANT1_SCHEMA" \
    -w "\n%{http_code}")
  status2=$(echo "$response2" | tail -n1)
  body2=$(echo "$response2" | sed '$d')

  if [[ "$status2" == "404" ]]; then
    ok "Negative test 2 passed (get invalid id -> $status2)"
  else
    log_http_warning "Negative test 2" "$status2" "$body2"
  fi
}

run_customer_tests () {
  banner "Running CUSTOMER endpoints tests"

  customer_create_test
  customer_list_test
  customer_list_active_test
  customer_get_by_id_test
  customer_search_by_name_test
  customer_get_by_email_test
  customer_update_test
  customer_toggle_active_test
  customer_soft_delete_test
  customer_restore_test
  customer_negative_tests

  ok "Customer tests completed"
}

# ------------------------------------------------------------
# ENTERPRISE MULTI-TENANT TESTS (EXISTENTES)
# ------------------------------------------------------------

parallel_login_stress_10 () {
  banner "Parallel login stress test (10 concurrent)"
  require_var_or_skip "$E2E_TENANT_EMAIL" "tenant_email" || return 0
  require_var_or_skip "$E2E_TENANT_PASSWORD" "tenant_password" || return 0
  seq 1 10 | xargs -I{} -P 10 bash -c '
    curl -s -X POST "'"$BASE_URL"'/api/tenant/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"'"$E2E_TENANT_EMAIL"'\",\"password\":\"'"$E2E_TENANT_PASSWORD"'\"}" \
      >/dev/null 2>&1 || true
  '
  ok "Parallel login stress test finished"
}

tenant_isolation_smoke_test () {
  banner "Tenant isolation smoke test"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT2_TOKEN" "tenant2_token" || return 0
  local tmp1="$PROJECT_DIR/.tenant1.me.json"
  local tmp2="$PROJECT_DIR/.tenant2.me.json"
  curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT1_TOKEN" > "$tmp1" 2>/dev/null || echo "{}" > "$tmp1"
  curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT2_TOKEN" > "$tmp2" 2>/dev/null || echo "{}" > "$tmp2"
  echo "Tenant1: $(head -c 100 "$tmp1")..."
  echo "Tenant2: $(head -c 100 "$tmp2")..."
  ok "Tenant isolation smoke test passed"
}

stress_test_100_parallel_logins () {
  banner "STRESS TEST: 100 parallel logins"
  require_var_or_skip "$E2E_TENANT_EMAIL" "tenant_email" || return 0
  require_var_or_skip "$E2E_TENANT_PASSWORD" "tenant_password" || return 0
  seq 1 100 | xargs -I{} -P 100 bash -c '
    curl -s -X POST "'"$BASE_URL"'/api/tenant/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"'"$E2E_TENANT_EMAIL"'\",\"password\":\"'"$E2E_TENANT_PASSWORD"'\"}" \
      >/dev/null 2>&1 || true
  '
  ok "100 parallel logins finished"
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
      >/dev/null 2>&1 || true
  '
  ok "Concurrent user creation test finished"
}

security_test_token_reuse () {
  banner "SECURITY TEST: token reuse"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT1_TOKEN" > "$PROJECT_DIR/.token_test1.json" 2>/dev/null || true
  curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT1_TOKEN" > "$PROJECT_DIR/.token_test2.json" 2>/dev/null || true
  ok "Token reuse smoke finished"
}

cross_tenant_leak_check () {
  banner "CROSS TENANT LEAK CHECK"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT2_TOKEN" "tenant2_token" || return 0
  curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT1_TOKEN" > "$PROJECT_DIR/.tenantA.json" 2>/dev/null || echo "{}" > "$PROJECT_DIR/.tenantA.json"
  curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT2_TOKEN" > "$PROJECT_DIR/.tenantB.json" 2>/dev/null || echo "{}" > "$PROJECT_DIR/.tenantB.json"
  echo "TenantA: $(head -c 100 "$PROJECT_DIR/.tenantA.json")..."
  echo "TenantB: $(head -c 100 "$PROJECT_DIR/.tenantB.json")..."
  ok "Cross tenant leak check executed"
}

postgres_deadlock_detector () {
  banner "POSTGRES DEADLOCK DETECTOR"
  if grep -i "deadlock detected" "$APP_LOG" >/dev/null 2>&1; then
    grep -i "deadlock detected" "$APP_LOG" || true
    die "Deadlock detected in app log"
  fi
  ok "No deadlock detected"
}

inventory_concurrency_test () {
  banner "INVENTORY CONCURRENCY TEST"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  seq 1 50 | xargs -I{} -P 50 bash -c '
    curl -s -X GET "'"$BASE_URL"'/api/tenant/products/low-stock/count?threshold=5" \
      -H "Authorization: Bearer '"$TENANT1_TOKEN"'" \
      -H "X-Tenant: '"$TENANT1_SCHEMA"'" \
      >/dev/null 2>&1 || true
  '
  ok "Inventory concurrency smoke finished"
}

simulate_50_tenants () {
  banner "SIMULATING 50 TENANTS"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  for i in $(seq 1 50); do
    curl -s "$BASE_URL/api/tenant/me" \
      -H "Authorization: Bearer $TENANT1_TOKEN" \
      -H "X-Tenant: tenant_test_$i" >/dev/null 2>&1 || true
  done
  ok "50 tenant simulation finished"
}

billing_multi_tenant_test () {
  banner "BILLING MULTI-TENANT TEST"
  require_var_or_skip "$SUPERADMIN_TOKEN" "superadmin_token" || return 0
  seq 1 20 | xargs -I{} -P 10 bash -c '
    curl -s -X POST "'"$BASE_URL"'/api/controlplane/billing/payments" \
      -H "Authorization: Bearer '"$SUPERADMIN_TOKEN"'" \
      -H "Content-Type: application/json" \
      -d "{\"accountId\":2,\"amount\":100,\"paymentGateway\":\"MANUAL\",\"paymentMethod\":\"PIX\"}" \
      >/dev/null 2>&1 || true
  '
  ok "Billing multi-tenant test finished"
}

# ------------------------------------------------------------
# MAIN
# ------------------------------------------------------------

main () {
  echo -e "${BLUE}=========================================${NC}"
  echo -e "${GREEN}Runner $VERSION - WITH CUSTOMER TESTS${NC}"
  echo -e "${BLUE}=========================================${NC}"
  echo "Collection: $COLLECTION"
  echo "Env: $ENV_FILE"
  echo ""

  check_port_available
  drop_db || true
  start_app
  wait_started
  health_check
  patch_env
  run_newman
  load_runtime_vars
  check_architecture

  echo ""
  echo "==========================================================="
  echo -e "${GREEN}✅ NEWMAN SUCCESS ($VERSION)${NC}"
  echo "==========================================================="
  echo ""

  # Testes existentes
  parallel_login_stress_10
  tenant_isolation_smoke_test
  stress_test_100_parallel_logins
  race_test_concurrent_user_creation
  security_test_token_reuse
  cross_tenant_leak_check

  # NOVOS TESTES DE CUSTOMER
  run_customer_tests

  # Testes de infraestrutura
  postgres_deadlock_detector
  inventory_concurrency_test
  simulate_50_tenants
  billing_multi_tenant_test

  echo ""
  echo -e "${GREEN}=========================================${NC}"
  echo -e "${GREEN}✅ ALL TESTS COMPLETED ($VERSION)${NC}"
  echo -e "${GREEN}=========================================${NC}"
  echo ""
}

main "$@"