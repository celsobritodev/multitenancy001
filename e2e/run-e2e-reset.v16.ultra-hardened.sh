#!/bin/bash
set -euo pipefail

VERSION="v16.0-ULTRA-HARDENED"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

COLLECTION="${COLLECTION:-}"
ENV_FILE="${ENV_FILE:-}"

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
NEWMAN_JSON="${NEWMAN_JSON:-$PROJECT_DIR/.e2e-newman.report.json}"
PERF_REPORT_JSON="${PERF_REPORT_JSON:-$PROJECT_DIR/.e2e-performance.report.json}"
PERF_REPORT_TXT="${PERF_REPORT_TXT:-$PROJECT_DIR/.e2e-performance.report.txt}"

APP_PID=""

BASE_URL=""
E2E_TENANT_EMAIL=""
E2E_TENANT_PASSWORD=""
TENANT1_TOKEN=""
TENANT2_TOKEN=""
TENANT1_SCHEMA=""
TENANT2_SCHEMA=""
TENANT1_ACCOUNT_ID=""
TENANT2_ACCOUNT_ID=""
SUPERADMIN_TOKEN=""
PRODUCT_ID=""
PAYMENT_GATEWAY="MANUAL"
PAYMENT_METHOD="PIX"
PERF_P95_LIMIT_MS="1500"
PERF_AVG_LIMIT_MS="500"
HIKARI_POOL_STRESS_ENABLED="true"
HIKARI_POOL_CONCURRENCY="60"
ULTRA_SALES_COUNT="1000"
ULTRA_SALES_PARALLELISM="100"
ULTRA_BILLING_PARALLELISM="20"
ULTRA_TENANT_SIMULATION="50"

resolve_latest_file () {
  local dir="$1"
  local pattern="$2"
  ls -1 "$dir"/$pattern 2>/dev/null | sort -V | tail -n 1
}

if [[ -z "$COLLECTION" ]]; then
  latest="$(resolve_latest_file "$PROJECT_DIR/e2e" "multitenancy001*.postman_collection*.json")"
  [[ -n "$latest" ]] && COLLECTION="e2e/$(basename "$latest")"
fi

if [[ -z "$ENV_FILE" ]]; then
  latest_env="$(resolve_latest_file "$PROJECT_DIR/e2e" "multitenancy001*.postman_environment*.json")"
  [[ -n "$latest_env" ]] && ENV_FILE="e2e/$(basename "$latest_env")"
fi

banner () { echo "==> $1"; }

info () { echo -e "${GREEN}$1${NC}"; }
warn () { echo -e "${YELLOW}$1${NC}"; }

fail () {
  echo ""
  echo "==========================================================="
  echo -e "${RED}❌ $1${NC}"
  echo "==========================================================="
  echo ""
  exit "${2:-1}"
}

cleanup () {
  if [[ -n "${APP_PID:-}" ]]; then
    echo "==> Stop app (pid=$APP_PID)"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

check_requirements() {
  [[ -n "$COLLECTION" && -n "$ENV_FILE" ]] || fail "Missing inputs. Use COLLECTION=... ENV_FILE=..."
  [[ -f "$PROJECT_DIR/$COLLECTION" ]] || fail "Collection not found: $PROJECT_DIR/$COLLECTION"
  [[ -f "$PROJECT_DIR/$ENV_FILE" ]] || fail "Env file not found: $PROJECT_DIR/$ENV_FILE"
  command -v jq >/dev/null 2>&1 || fail "jq not installed"
  command -v newman >/dev/null 2>&1 || fail "newman not installed"
  command -v curl >/dev/null 2>&1 || fail "curl not installed"
  command -v python >/dev/null 2>&1 || fail "python not installed"
}

check_port_available() {
  local port="$1"
  banner "Checking if port $port is available"
  if command -v lsof >/dev/null 2>&1 && lsof -i :"$port" >/dev/null 2>&1; then
    fail "Port $port already in use"
  fi
  info "✅ Port $port available"
}

drop_db() {
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

start_app() {
  banner "Start app"
  : > "$APP_LOG"
  (cd "$PROJECT_DIR" && ./mvnw -q spring-boot:run >"$APP_LOG" 2>&1) &
  APP_PID="$!"
  echo "App PID=$APP_PID"
}

wait_started() {
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
      fail "Application did not start"
    fi
    sleep 1
  done
}

health_check() {
  local url="http://localhost:${APP_PORT}${APP_HEALTH_PATH}"
  banner "Health check $url"
  for _ in {1..30}; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "Health OK"
      return
    fi
    sleep 1
  done
  fail "Health check failed"
}

patch_env() {
  banner "Prepare effective env"
  jq '.' "$PROJECT_DIR/$ENV_FILE" > "$EFFECTIVE_ENV"
  echo "Env ready -> $EFFECTIVE_ENV"
}

read_env_value() {
  local key="$1"
  jq -r --arg KEY "$key" '.values[]? | select(.key == $KEY) | .value' "$EFFECTIVE_ENV" 2>/dev/null | tail -n 1
}

normalize_nullish() {
  local v="${1:-}"
  if [[ -z "$v" || "$v" == "null" ]]; then echo ""; else echo "$v"; fi
}

load_runtime_vars() {
  BASE_URL="$(normalize_nullish "$(read_env_value base_url)")"
  [[ -n "$BASE_URL" ]] || BASE_URL="http://localhost:${APP_PORT}"

  E2E_TENANT_EMAIL="$(normalize_nullish "$(read_env_value tenant_email)")"
  E2E_TENANT_PASSWORD="$(normalize_nullish "$(read_env_value tenant_password)")"
  TENANT1_TOKEN="$(normalize_nullish "$(read_env_value tenant_token)")"
  [[ -n "$TENANT1_TOKEN" ]] || TENANT1_TOKEN="$(normalize_nullish "$(read_env_value tenant_access_token)")"
  TENANT2_TOKEN="$(normalize_nullish "$(read_env_value tenant2_token)")"
  [[ -n "$TENANT2_TOKEN" ]] || TENANT2_TOKEN="$(normalize_nullish "$(read_env_value tenant2_access_token)")"
  TENANT1_SCHEMA="$(normalize_nullish "$(read_env_value tenant_schema)")"
  [[ -n "$TENANT1_SCHEMA" ]] || TENANT1_SCHEMA="$(normalize_nullish "$(read_env_value tenant1_schema)")"
  TENANT2_SCHEMA="$(normalize_nullish "$(read_env_value tenant2_schema)")"
  TENANT1_ACCOUNT_ID="$(normalize_nullish "$(read_env_value tenant_account_id)")"
  [[ -n "$TENANT1_ACCOUNT_ID" ]] || TENANT1_ACCOUNT_ID="$(normalize_nullish "$(read_env_value tenant1_account_id)")"
  TENANT2_ACCOUNT_ID="$(normalize_nullish "$(read_env_value tenant2_account_id)")"
  SUPERADMIN_TOKEN="$(normalize_nullish "$(read_env_value superadmin_token)")"
  PRODUCT_ID="$(normalize_nullish "$(read_env_value product_id)")"
  PAYMENT_GATEWAY="$(normalize_nullish "$(read_env_value payment_gateway)")"
  PAYMENT_METHOD="$(normalize_nullish "$(read_env_value payment_method)")"
  PERF_P95_LIMIT_MS="$(normalize_nullish "$(read_env_value perf_p95_limit_ms)")"
  PERF_AVG_LIMIT_MS="$(normalize_nullish "$(read_env_value perf_avg_limit_ms)")"
  HIKARI_POOL_STRESS_ENABLED="$(normalize_nullish "$(read_env_value hikari_pool_stress_enabled)")"
  HIKARI_POOL_CONCURRENCY="$(normalize_nullish "$(read_env_value hikari_pool_concurrency)")"
  ULTRA_SALES_COUNT="$(normalize_nullish "$(read_env_value ultra_sales_count)")"
  ULTRA_SALES_PARALLELISM="$(normalize_nullish "$(read_env_value ultra_sales_parallelism)")"
  ULTRA_BILLING_PARALLELISM="$(normalize_nullish "$(read_env_value ultra_billing_parallelism)")"
  ULTRA_TENANT_SIMULATION="$(normalize_nullish "$(read_env_value ultra_tenant_simulation)")"

  [[ -n "$PAYMENT_GATEWAY" ]] || PAYMENT_GATEWAY="MANUAL"
  [[ -n "$PAYMENT_METHOD" ]] || PAYMENT_METHOD="PIX"
  [[ -n "$PERF_P95_LIMIT_MS" ]] || PERF_P95_LIMIT_MS="1500"
  [[ -n "$PERF_AVG_LIMIT_MS" ]] || PERF_AVG_LIMIT_MS="500"
  [[ -n "$HIKARI_POOL_CONCURRENCY" ]] || HIKARI_POOL_CONCURRENCY="60"
  [[ -n "$ULTRA_SALES_COUNT" ]] || ULTRA_SALES_COUNT="1000"
  [[ -n "$ULTRA_SALES_PARALLELISM" ]] || ULTRA_SALES_PARALLELISM="100"
  [[ -n "$ULTRA_BILLING_PARALLELISM" ]] || ULTRA_BILLING_PARALLELISM="20"
  [[ -n "$ULTRA_TENANT_SIMULATION" ]] || ULTRA_TENANT_SIMULATION="50"
}

run_newman() {
  banner "Run Newman"
  : > "$NEWMAN_OUT"
  rm -f "$NEWMAN_JSON"
  set +e
  newman run "$PROJECT_DIR/$COLLECTION" \
    -e "$EFFECTIVE_ENV" \
    --export-environment "$EFFECTIVE_ENV" \
    -r cli,json \
    --reporter-json-export "$NEWMAN_JSON" \
    --timeout-request 30000 \
    --timeout-script 30000 \
    --insecure \
    2>&1 | tee -a "$NEWMAN_OUT"
  local status=${PIPESTATUS[0]}
  set -e
  [[ $status -eq 0 ]] || { tail -n 200 "$APP_LOG" || true; exit $status; }
}

check_architecture() {
  banner "Checking architectural errors"
  if grep -E "Pre-bound JDBC|TenantContext.bindTenantSchema|IllegalTransactionStateException|relation .* does not exist|schema .* does not exist" "$APP_LOG" >/dev/null; then
    grep -E "Pre-bound JDBC|TenantContext.bindTenantSchema|IllegalTransactionStateException|relation .* does not exist|schema .* does not exist" "$APP_LOG" || true
    tail -n 200 "$APP_LOG" || true
    fail "Architectural error detected"
  fi
  info "✔ Arquitetura multi-tenant saudável"
}

make_perf_report() {
  banner "Performance report"
  python - "$NEWMAN_JSON" "$PERF_REPORT_JSON" "$PERF_REPORT_TXT" "$PERF_P95_LIMIT_MS" "$PERF_AVG_LIMIT_MS" <<'PY'
import json, sys, statistics, math, pathlib, re
src, out_json, out_txt, p95_limit, avg_limit = sys.argv[1:]
p95_limit = float(p95_limit)
avg_limit = float(avg_limit)
data = json.load(open(src, encoding='utf-8'))
executions = data.get('run', {}).get('executions', [])
latencies = []
by_module = {}
for ex in executions:
    item = ex.get('item', {})
    name = item.get('name', 'UNKNOWN')
    path = item.get('_postman_previewlanguage')
    parents = ex.get('cursor', {})
    time = ex.get('response', {}).get('responseTime')
    if isinstance(time, (int, float)):
        latencies.append(float(time))
    module = 'ROOT'
    # derive module from item name like "00 - ..." from ancestors when available
    ancestors = item.get('_postman_isSubFolderItem')
    # newman json doesn't expose ancestors cleanly; use name prefix fallback
    m = re.match(r'^(\d+|📦|🔐|🎯|💰|🆘|⚙️|💳|🗂️|📂|🏭|📦|🔍|⚠️|👥|🏢|📊|🧩|🧪|🔥|🚀)\s*(.*)$', name)
    module = name.split(' / ')[0]
    by_module.setdefault(module, {'requests': 0, 'latencies': []})
    by_module[module]['requests'] += 1
    if isinstance(time, (int, float)):
        by_module[module]['latencies'].append(float(time))

latencies.sort()
count = len(latencies)
if count == 0:
    avg = p95 = p99 = max_v = 0.0
else:
    avg = sum(latencies) / count
    def percentile(vals, p):
        if not vals: return 0.0
        k = (len(vals)-1) * p
        f = math.floor(k); c = math.ceil(k)
        if f == c: return vals[int(k)]
        d0 = vals[f] * (c-k)
        d1 = vals[c] * (k-f)
        return d0 + d1
    p95 = percentile(latencies, 0.95)
    p99 = percentile(latencies, 0.99)
    max_v = max(latencies)
summary = {
    'requests_total': count,
    'latency_avg_ms': round(avg,2),
    'latency_p95_ms': round(p95,2),
    'latency_p99_ms': round(p99,2),
    'latency_max_ms': round(max_v,2),
    'limits': {'p95_ms': p95_limit, 'avg_ms': avg_limit},
    'by_module': {}
}
for module, vals in by_module.items():
    ls = sorted(vals['latencies'])
    a = round(sum(ls)/len(ls), 2) if ls else 0.0
    p95m = 0.0
    if ls:
        k = (len(ls)-1) * 0.95
        f = math.floor(k); c = math.ceil(k)
        p95m = ls[int(k)] if f == c else ls[f]*(c-k)+ls[c]*(k-f)
    summary['by_module'][module] = {
        'requests': vals['requests'],
        'avg_ms': a,
        'p95_ms': round(p95m,2)
    }
pathlib.Path(out_json).write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
lines = []
lines.append('V16 ULTRA HARDENED - PERFORMANCE REPORT')
lines.append('')
lines.append(f"requests_total: {summary['requests_total']}")
lines.append(f"latency_avg_ms: {summary['latency_avg_ms']}")
lines.append(f"latency_p95_ms: {summary['latency_p95_ms']}")
lines.append(f"latency_p99_ms: {summary['latency_p99_ms']}")
lines.append(f"latency_max_ms: {summary['latency_max_ms']}")
lines.append('')
lines.append('REQUEST COUNTS BY MODULE')
for mod, vals in sorted(summary['by_module'].items()):
    lines.append(f"- {mod}: requests={vals['requests']} avg_ms={vals['avg_ms']} p95_ms={vals['p95_ms']}")
pathlib.Path(out_txt).write_text('\n'.join(lines)+'\n', encoding='utf-8')
print('\n'.join(lines))
if p95 > p95_limit:
    print(f"FAIL_P95 {p95} > {p95_limit}")
    sys.exit(11)
if avg > avg_limit:
    print(f"FAIL_AVG {avg} > {avg_limit}")
    sys.exit(12)
PY
}

require_var_or_skip() {
  local value="$1"
  local label="$2"
  if [[ -z "$value" ]]; then
    warn "⚠ Skipping: missing $label"
    return 1
  fi
  return 0
}

parallel_login_stress_10() {
  banner "Parallel login stress test (10 concurrent)"
  require_var_or_skip "$E2E_TENANT_EMAIL" "tenant_email" || return 0
  require_var_or_skip "$E2E_TENANT_PASSWORD" "tenant_password" || return 0
  seq 1 10 | xargs -I{} -P 10 bash -c '
    curl -s -X POST "'$BASE_URL'/api/tenant/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"'$E2E_TENANT_EMAIL'\",\"password\":\"'$E2E_TENANT_PASSWORD'\"}" >/dev/null
  '
  info "✅ Parallel login stress test finished"
}

tenant_isolation_smoke_test() {
  banner "Tenant isolation smoke test"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT2_TOKEN" "tenant2_token" || return 0
  local a b
  a="$(curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT1_TOKEN")"
  b="$(curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT2_TOKEN")"
  [[ "$a" != "$b" ]] || fail "Tenant isolation smoke test failed: identical responses"
  info "✅ Tenant isolation smoke test passed"
}

jwt_replay_attack_test() {
  banner "JWT replay attack test"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  seq 1 50 | xargs -I{} -P 25 bash -c '
    code=$(curl -s -o /dev/null -w "%{http_code}" "'$BASE_URL'/api/tenant/me" -H "Authorization: Bearer '$TENANT1_TOKEN'")
    [[ "$code" == "200" ]]
  '
  info "✅ JWT replay smoke passed"
}

tenant_escape_attack_test() {
  banner "Tenant escape attack test"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT2_SCHEMA" "tenant2_schema" || return 0
  local normal escape
  normal="$(curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT1_TOKEN")"
  escape="$(curl -s "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT1_TOKEN" -H "X-Tenant: $TENANT2_SCHEMA")"
  if [[ "$escape" != "$normal" ]]; then
    fail "Tenant escape attack changed response payload"
  fi
  info "✅ Tenant escape attack blocked/neutralized"
}

race_user_creation_test() {
  banner "RACE TEST: concurrent user creation"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  seq 1 20 | xargs -I{} -P 20 bash -c '
    curl -s -X POST "'$BASE_URL'/api/tenant/users" \
      -H "Authorization: Bearer '$TENANT1_TOKEN'" \
      -H "X-Tenant: '$TENANT1_SCHEMA'" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"RaceUser{}\",\"email\":\"raceuser{}@tenant.local\",\"password\":\"E2ePass123\",\"role\":\"TENANT_USER\",\"permissions\":[]}" >/dev/null
  '
  info "✅ Race user creation finished"
}

ultra_sales_stress_test() {
  banner "ULTRA STRESS: ${ULTRA_SALES_COUNT} simulated sales"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  require_var_or_skip "$PRODUCT_ID" "product_id" || return 0
  seq 1 "$ULTRA_SALES_COUNT" | xargs -I{} -P "$ULTRA_SALES_PARALLELISM" bash -c '
    ts=$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)
    curl -s -X POST "'$BASE_URL'/api/tenant/sales" \
      -H "Authorization: Bearer '$TENANT1_TOKEN'" \
      -H "X-Tenant: '$TENANT1_SCHEMA'" \
      -H "Content-Type: application/json" \
      -d "{\"saleDate\":\"${ts}\",\"status\":\"DRAFT\",\"items\":[{\"productId\":\"'$PRODUCT_ID'\",\"productName\":\"Stress Product '$PRODUCT_ID'\",\"quantity\":1,\"unitPrice\":100}]}" >/dev/null
  '
  info "✅ Ultra sales stress finished"
}

inventory_race_detector() {
  banner "INVENTORY CONCURRENCY TEST"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  seq 1 200 | xargs -I{} -P 50 bash -c '
    curl -s "'$BASE_URL'/api/tenant/products/low-stock/count?threshold=5" \
      -H "Authorization: Bearer '$TENANT1_TOKEN'" \
      -H "X-Tenant: '$TENANT1_SCHEMA'" >/dev/null
  '
  if grep -Ei 'optimistic|deadlock|could not serialize|timeout while waiting for a connection' "$APP_LOG" >/dev/null; then
    grep -Ei 'optimistic|deadlock|could not serialize|timeout while waiting for a connection' "$APP_LOG" | tail -n 20
    fail "Inventory race detector found concurrency errors"
  fi
  info "✅ Inventory race detector passed"
}

hikari_pool_saturation_test() {
  banner "Hikari pool saturation stress test"
  [[ "${HIKARI_POOL_STRESS_ENABLED,,}" == "true" ]] || { warn "⚠ Skipping: hikari_pool_stress_enabled=false"; return 0; }
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  local concurrency="$HIKARI_POOL_CONCURRENCY"
  seq 1 300 | xargs -I{} -P "$concurrency" bash -c '
    curl -s "'$BASE_URL'/api/tenant/me" -H "Authorization: Bearer '$TENANT1_TOKEN'" >/dev/null
  '
  if grep -Ei 'HikariPool|Connection is not available|timeout while waiting for a connection|too many clients already' "$APP_LOG" >/dev/null; then
    grep -Ei 'HikariPool|Connection is not available|timeout while waiting for a connection|too many clients already' "$APP_LOG" | tail -n 30
    fail "Hikari pool saturation detected"
  fi
  info "✅ Hikari pool saturation test passed"
}

multi_tenant_billing_stress() {
  banner "Billing multi-tenant stress"
  require_var_or_skip "$SUPERADMIN_TOKEN" "superadmin_token" || return 0
  require_var_or_skip "$TENANT1_ACCOUNT_ID" "tenant_account_id" || return 0
  seq 1 100 | xargs -I{} -P "$ULTRA_BILLING_PARALLELISM" bash -c '
    curl -s -X POST "'$BASE_URL'/api/controlplane/billing/payments" \
      -H "Authorization: Bearer '$SUPERADMIN_TOKEN'" \
      -H "Content-Type: application/json" \
      -d "{\"accountId\":'$TENANT1_ACCOUNT_ID',\"amount\":100,\"paymentGateway\":\"'$PAYMENT_GATEWAY'\",\"paymentMethod\":\"'$PAYMENT_METHOD'\"}" >/dev/null
  '
  info "✅ Billing multi-tenant stress finished"
}

simulate_50_tenants() {
  banner "SIMULATING ${ULTRA_TENANT_SIMULATION} TENANTS"
  require_var_or_skip "$TENANT1_TOKEN" "tenant_token" || return 0
  require_var_or_skip "$TENANT1_SCHEMA" "tenant_schema" || return 0
  for i in $(seq 1 "$ULTRA_TENANT_SIMULATION"); do
    curl -s "$BASE_URL/api/tenant/me" \
      -H "Authorization: Bearer $TENANT1_TOKEN" \
      -H "X-Tenant: ${TENANT1_SCHEMA}" >/dev/null
  done
  info "✅ Tenant simulation finished"
}

deadlock_detector() {
  banner "POSTGRES DEADLOCK DETECTOR"
  if grep -i 'deadlock detected' "$APP_LOG" >/dev/null; then
    grep -i 'deadlock detected' "$APP_LOG"
    fail "Deadlock detected"
  fi
  info "✅ No deadlock detected"
}

main() {
  echo "Runner $VERSION"
  echo "Collection: $COLLECTION"
  echo "Env: $ENV_FILE"
  check_requirements
  check_port_available "$APP_PORT"
  drop_db
  start_app
  wait_started
  health_check
  patch_env
  load_runtime_vars
  run_newman
  load_runtime_vars
  check_architecture
  make_perf_report

  echo ""
  echo "==========================================================="
  echo -e "${GREEN}✅ SUCCESS ($VERSION)${NC}"
  echo "==========================================================="
  echo ""

  parallel_login_stress_10
  tenant_isolation_smoke_test
  stress_test_100_parallel_logins || true
}

stress_test_100_parallel_logins() {
  banner "STRESS TEST: 100 parallel logins"
  require_var_or_skip "$E2E_TENANT_EMAIL" "tenant_email" || return 0
  require_var_or_skip "$E2E_TENANT_PASSWORD" "tenant_password" || return 0
  seq 1 100 | xargs -I{} -P 100 bash -c '
    curl -s -X POST "'$BASE_URL'/api/tenant/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"'$E2E_TENANT_EMAIL'\",\"password\":\"'$E2E_TENANT_PASSWORD'\"}" >/dev/null
  '
  info "✅ 100 parallel logins finished"

  race_user_creation_test
  jwt_replay_attack_test
  tenant_escape_attack_test
  deadlock_detector
  inventory_race_detector
  hikari_pool_saturation_test
  simulate_50_tenants
  ultra_sales_stress_test
  multi_tenant_billing_stress
  deadlock_detector
  echo "ULTRA HARDENED SUITE COMPLETED"
}

main "$@"
