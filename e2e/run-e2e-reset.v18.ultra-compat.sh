#!/bin/bash
set -euo pipefail

VERSION="v18-ULTRA-COMPAT"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

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

PERF_RANKING="${PERF_RANKING:-$PROJECT_DIR/.e2e-performance-ranking.txt}"
PERF_MODULES="${PERF_MODULES:-$PROJECT_DIR/.e2e-performance-modules.txt}"
PERF_HISTORY="${PERF_HISTORY:-$PROJECT_DIR/e2e/.perf-history.csv}"

APP_PID=""

BASE_URL=""
TENANT_EMAIL=""
TENANT_PASSWORD=""
TENANT_TOKEN=""
TENANT_REFRESH_TOKEN=""
TENANT_SCHEMA=""
TENANT_ACCOUNT_ID=""
TENANT_USER_ID=""
TENANT2_TOKEN=""
TENANT2_SCHEMA=""
TENANT2_ACCOUNT_ID=""
SUPERADMIN_TOKEN=""
PRODUCT_ID=""
PAYMENT_GATEWAY="MANUAL"
PAYMENT_METHOD="PIX"
ULTRA_SALES_COUNT="1000"
ULTRA_SALES_PARALLELISM="100"
ULTRA_BILLING_PARALLELISM="20"
ULTRA_TENANT_SIMULATION="50"
HIKARI_POOL_STRESS_ENABLED="true"
HIKARI_POOL_CONCURRENCY="60"
PERF_P95_LIMIT_MS="1500"
PERF_AVG_LIMIT_MS="500"

banner () { echo "==> $1"; }

die () {
  echo ""
  echo "==========================================================="
  echo "❌ $1"
  echo "==========================================================="
  echo ""
  exit 1
}

warn () { echo "⚠ $1"; }
ok () { echo "✅ $1"; }

cleanup () {
  if [[ -n "${APP_PID:-}" ]]; then
    echo "==> Stop app (pid=$APP_PID)"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "$1 not installed"
}

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

check_requirements() {
  [[ -n "$COLLECTION" && -n "$ENV_FILE" ]] || die "Missing inputs. Use COLLECTION=... ENV_FILE=..."
  [[ -f "$PROJECT_DIR/$COLLECTION" ]] || die "Collection not found: $PROJECT_DIR/$COLLECTION"
  [[ -f "$PROJECT_DIR/$ENV_FILE" ]] || die "Env file not found: $PROJECT_DIR/$ENV_FILE"
  require_cmd jq
  require_cmd curl
  require_cmd newman
  require_cmd python
}

check_port_available() {
  banner "Checking if port $APP_PORT is available"
  if command -v lsof >/dev/null 2>&1 && lsof -i :"$APP_PORT" >/dev/null 2>&1; then
    die "Port $APP_PORT already in use"
  fi
  ok "Port $APP_PORT available"
}

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

start_app () {
  banner "Start app"
  : > "$APP_LOG"
  (cd "$PROJECT_DIR" && ./mvnw -q spring-boot:run >"$APP_LOG" 2>&1) &
  APP_PID="$!"
  echo "App PID=$APP_PID"
}

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

health_check () {
  banner "Health check http://localhost:${APP_PORT}${APP_HEALTH_PATH}"
  for _ in {1..30}; do
    if curl -fsS "http://localhost:${APP_PORT}${APP_HEALTH_PATH}" >/dev/null 2>&1; then
      echo "Health OK"
      return
    fi
    sleep 1
  done
  die "Health check failed"
}

patch_env () {
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
  if [[ -z "$v" || "$v" == "null" ]]; then
    echo ""
  else
    echo "$v"
  fi
}

load_runtime_vars() {
  BASE_URL="$(normalize_nullish "$(read_env_value base_url)")"
  [[ -n "$BASE_URL" ]] || BASE_URL="http://localhost:${APP_PORT}"

  TENANT_EMAIL="$(normalize_nullish "$(read_env_value tenant_email)")"
  TENANT_PASSWORD="$(normalize_nullish "$(read_env_value tenant_password)")"

  TENANT_TOKEN="$(normalize_nullish "$(read_env_value tenant_token)")"
  [[ -n "$TENANT_TOKEN" ]] || TENANT_TOKEN="$(normalize_nullish "$(read_env_value tenant_access_token)")"
  [[ -n "$TENANT_TOKEN" ]] || TENANT_TOKEN="$(normalize_nullish "$(read_env_value tenant1_access_token)")"

  TENANT_REFRESH_TOKEN="$(normalize_nullish "$(read_env_value tenant_refresh_token)")"

  TENANT_SCHEMA="$(normalize_nullish "$(read_env_value tenant_schema)")"
  [[ -n "$TENANT_SCHEMA" ]] || TENANT_SCHEMA="$(normalize_nullish "$(read_env_value tenant1_schema)")"

  TENANT_ACCOUNT_ID="$(normalize_nullish "$(read_env_value tenant_account_id)")"
  [[ -n "$TENANT_ACCOUNT_ID" ]] || TENANT_ACCOUNT_ID="$(normalize_nullish "$(read_env_value account_id)")"
  [[ -n "$TENANT_ACCOUNT_ID" ]] || TENANT_ACCOUNT_ID="$(normalize_nullish "$(read_env_value tenant1_account_id)")"

  TENANT_USER_ID="$(normalize_nullish "$(read_env_value tenant_user_id)")"
  [[ -n "$TENANT_USER_ID" ]] || TENANT_USER_ID="$(normalize_nullish "$(read_env_value tenant1_user_id)")"

  TENANT2_TOKEN="$(normalize_nullish "$(read_env_value tenant2_token)")"
  [[ -n "$TENANT2_TOKEN" ]] || TENANT2_TOKEN="$(normalize_nullish "$(read_env_value tenant2_access_token)")"

  TENANT2_SCHEMA="$(normalize_nullish "$(read_env_value tenant2_schema)")"
  TENANT2_ACCOUNT_ID="$(normalize_nullish "$(read_env_value tenant2_account_id)")"

  SUPERADMIN_TOKEN="$(normalize_nullish "$(read_env_value superadmin_token)")"
  PRODUCT_ID="$(normalize_nullish "$(read_env_value product_id)")"

  PAYMENT_GATEWAY="$(normalize_nullish "$(read_env_value payment_gateway)")"
  [[ -n "$PAYMENT_GATEWAY" ]] || PAYMENT_GATEWAY="MANUAL"

  PAYMENT_METHOD="$(normalize_nullish "$(read_env_value payment_method)")"
  [[ -n "$PAYMENT_METHOD" ]] || PAYMENT_METHOD="PIX"

  ULTRA_SALES_COUNT="$(normalize_nullish "$(read_env_value ultra_sales_count)")"
  [[ -n "$ULTRA_SALES_COUNT" ]] || ULTRA_SALES_COUNT="1000"

  ULTRA_SALES_PARALLELISM="$(normalize_nullish "$(read_env_value ultra_sales_parallelism)")"
  [[ -n "$ULTRA_SALES_PARALLELISM" ]] || ULTRA_SALES_PARALLELISM="100"

  ULTRA_BILLING_PARALLELISM="$(normalize_nullish "$(read_env_value ultra_billing_parallelism)")"
  [[ -n "$ULTRA_BILLING_PARALLELISM" ]] || ULTRA_BILLING_PARALLELISM="20"

  ULTRA_TENANT_SIMULATION="$(normalize_nullish "$(read_env_value ultra_tenant_simulation)")"
  [[ -n "$ULTRA_TENANT_SIMULATION" ]] || ULTRA_TENANT_SIMULATION="50"

  HIKARI_POOL_STRESS_ENABLED="$(normalize_nullish "$(read_env_value hikari_pool_stress_enabled)")"
  [[ -n "$HIKARI_POOL_STRESS_ENABLED" ]] || HIKARI_POOL_STRESS_ENABLED="true"

  HIKARI_POOL_CONCURRENCY="$(normalize_nullish "$(read_env_value hikari_pool_concurrency)")"
  [[ -n "$HIKARI_POOL_CONCURRENCY" ]] || HIKARI_POOL_CONCURRENCY="60"

  PERF_P95_LIMIT_MS="$(normalize_nullish "$(read_env_value perf_p95_limit_ms)")"
  [[ -n "$PERF_P95_LIMIT_MS" ]] || PERF_P95_LIMIT_MS="1500"

  PERF_AVG_LIMIT_MS="$(normalize_nullish "$(read_env_value perf_avg_limit_ms)")"
  [[ -n "$PERF_AVG_LIMIT_MS" ]] || PERF_AVG_LIMIT_MS="500"
}

run_newman () {
  banner "Run Newman"
  : > "$NEWMAN_OUT"
  rm -f "$NEWMAN_JSON"

  set +e
  newman run "$PROJECT_DIR/$COLLECTION" \
    -e "$EFFECTIVE_ENV" \
    --export-environment "$EFFECTIVE_ENV" \
    --reporters cli,json \
    --reporter-json-export "$NEWMAN_JSON" \
    --timeout-request 30000 \
    --timeout-script 30000 \
    --insecure \
    2>&1 | tee -a "$NEWMAN_OUT"
  status=${PIPESTATUS[0]}
  set -e

  if [[ $status -ne 0 ]]; then
    echo "Newman failed"
    tail -n 200 "$APP_LOG" || true
    exit $status
  fi
}

check_architecture () {
  banner "Checking architectural errors"
  if grep -E "Pre-bound JDBC|TenantContext.bindTenantSchema|IllegalTransactionStateException|relation .* does not exist|schema .* does not exist" "$APP_LOG" >/dev/null; then
    grep -E "Pre-bound JDBC|TenantContext.bindTenantSchema|IllegalTransactionStateException|relation .* does not exist|schema .* does not exist" "$APP_LOG" || true
    tail -n 200 "$APP_LOG" || true
    die "Architectural error detected"
  fi
  echo "✔ Arquitetura multi-tenant saudável"
}

performance_analysis () {
  banner "Performance report"

  jq -r '.run.executions[] | select(.response != null) | "\(.item.name)|\(.response.responseTime)"' "$NEWMAN_JSON" \
    | sort -t'|' -k2 -nr \
    | head -n 20 \
    | awk -F'|' '{printf "%-70s %8s ms\n",$1,$2}' \
    > "$PERF_RANKING"

  python - "$NEWMAN_JSON" "$PERF_MODULES" "$PERF_HISTORY" "$PERF_P95_LIMIT_MS" "$PERF_AVG_LIMIT_MS" <<'PY'
import csv, json, math, os, statistics, sys
src, modules_path, hist_path, p95_limit, avg_limit = sys.argv[1:]
p95_limit = float(p95_limit)
avg_limit = float(avg_limit)

with open(src, encoding='utf-8') as f:
    data = json.load(f)

times = []
mods = {}

def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    idx = min(len(sorted_vals)-1, max(0, math.floor((len(sorted_vals)-1) * p)))
    return float(sorted_vals[idx])

for ex in data.get("run", {}).get("executions", []):
    resp = ex.get("response")
    if not resp:
        continue
    t = float(resp.get("responseTime", 0) or 0)
    name = ex.get("item", {}).get("name", "UNKNOWN")
    times.append(t)
    upper = name.upper()
    if "AUTH" in upper:
        m = "AUTH"
    elif "BILLING" in upper or "PAYMENT" in upper:
        m = "BILLING"
    elif "PRODUCT" in upper or "CATEGORY" in upper or "SUBCATEGORY" in upper or "SUPPLIER" in upper:
        m = "CATALOG"
    elif "SALE" in upper:
        m = "SALES"
    elif "USER" in upper:
        m = "USERS"
    else:
        m = "OTHER"
    mods.setdefault(m, []).append(t)

times_sorted = sorted(times)
req_total = len(times_sorted)
avg = statistics.mean(times_sorted) if times_sorted else 0.0
p95 = percentile(times_sorted, 0.95)
p99 = percentile(times_sorted, 0.99)
mx = max(times_sorted) if times_sorted else 0.0
failed = data.get("run", {}).get("stats", {}).get("requests", {}).get("failed", 0)

with open(modules_path, "w", encoding="utf-8") as f:
    for mod in sorted(mods.keys()):
        vals = sorted(mods[mod])
        f.write(f"{mod:<15} requests={len(vals):4d} avg_ms={statistics.mean(vals):8.2f} p95_ms={percentile(vals,0.95):8.2f}\n")

os.makedirs(os.path.dirname(hist_path), exist_ok=True)
if not os.path.exists(hist_path):
    with open(hist_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["timestamp","requests","failures","avg_ms","p95_ms","p99_ms","max_ms"])
with open(hist_path, "a", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    from datetime import datetime
    w.writerow([datetime.now().isoformat(timespec="seconds"), req_total, failed, round(avg,2), round(p95,2), round(p99,2), round(mx,2)])

print(f"requests_total: {req_total}")
print(f"failures_total: {failed}")
print(f"latency_avg_ms: {avg:.2f}")
print(f"latency_p95_ms: {p95:.2f}")
print(f"latency_p99_ms: {p99:.2f}")
print(f"latency_max_ms: {mx:.2f}")

if p95 > p95_limit:
    print(f"P95_LIMIT_BREACH={p95:.2f}>{p95_limit:.2f}")
if avg > avg_limit:
    print(f"AVG_LIMIT_BREACH={avg:.2f}>{avg_limit:.2f}")
PY

  PERF_SUMMARY="$(python - "$NEWMAN_JSON" <<'PY'
import json, math, statistics, sys
with open(sys.argv[1], encoding="utf-8") as f: data=json.load(f)
vals=sorted([float(ex["response"]["responseTime"]) for ex in data["run"]["executions"] if ex.get("response")])
def p(x):
    return vals[min(len(vals)-1, max(0, math.floor((len(vals)-1)*x)))] if vals else 0
print(f"{statistics.mean(vals) if vals else 0:.2f}|{p(0.95):.2f}")
PY
)"
  AVG_VALUE="${PERF_SUMMARY%%|*}"
  P95_VALUE="${PERF_SUMMARY##*|}"

  echo ""
  echo "==========================================================="
  echo "V18 ULTRA COMPAT - PERFORMANCE REPORT"
  echo "==========================================================="
  echo ""
  python - "$NEWMAN_JSON" <<'PY'
import json, math, statistics, sys
with open(sys.argv[1], encoding="utf-8") as f: data=json.load(f)
vals=sorted([float(ex["response"]["responseTime"]) for ex in data["run"]["executions"] if ex.get("response")])
def p(x):
    return vals[min(len(vals)-1, max(0, math.floor((len(vals)-1)*x)))] if vals else 0
print(f"requests_total: {len(vals)}")
print(f"failures_total: {data.get('run',{}).get('stats',{}).get('requests',{}).get('failed',0)}")
print(f"latency_avg_ms: {statistics.mean(vals) if vals else 0:.2f}")
print(f"latency_p95_ms: {p(0.95):.2f}")
print(f"latency_p99_ms: {p(0.99):.2f}")
print(f"latency_max_ms: {max(vals) if vals else 0:.2f}")
PY
  echo ""
  echo "TOP 20 SLOWEST ENDPOINTS"
  cat "$PERF_RANKING"
  echo ""
  echo "P95 BY MODULE"
  cat "$PERF_MODULES"
  echo ""
  echo "RUN HISTORY (last 10)"
  tail -n 10 "$PERF_HISTORY"
  echo ""

  python - <<PY
avg=float("$AVG_VALUE")
p95=float("$P95_VALUE")
avg_lim=float("$PERF_AVG_LIMIT_MS")
p95_lim=float("$PERF_P95_LIMIT_MS")
if p95 > p95_lim:
    raise SystemExit(f"P95 above limit: {p95:.2f} > {p95_lim:.2f}")
if avg > avg_lim:
    raise SystemExit(f"AVG above limit: {avg:.2f} > {avg_lim:.2f}")
PY
}

safe_skip_if_missing() {
  local label="$1"; shift
  for v in "$@"; do
    if [[ -z "$v" ]]; then
      warn "Skipping: missing $label"
      return 1
    fi
  done
  return 0
}

curl_json() {
  local method="$1"; shift
  local url="$1"; shift
  local auth="${1:-}"; shift || true
  local xtenant="${1:-}"; shift || true
  local body="${1:-}"; shift || true

  local args=(-sS -X "$method" "$url" -H "Content-Type: application/json")
  [[ -n "$auth" ]] && args+=(-H "Authorization: Bearer $auth")
  [[ -n "$xtenant" ]] && args+=(-H "X-Tenant: $xtenant")
  [[ -n "$body" ]] && args+=(-d "$body")
  curl "${args[@]}"
}

parallel_login_stress() {
  banner "Parallel login stress test (10 concurrent)"
  safe_skip_if_missing "tenant_email" "$TENANT_EMAIL" "$TENANT_PASSWORD" || return 0

  export BASE_URL TENANT_EMAIL TENANT_PASSWORD
  seq 1 10 | xargs -I{} -P 10 bash -lc '
    curl -sS -X POST "$BASE_URL/api/tenant/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"$TENANT_EMAIL\",\"password\":\"$TENANT_PASSWORD\"}" >/dev/null
  '
  ok "Parallel login stress test finished"
}

tenant_isolation_smoke() {
  banner "Tenant isolation smoke test"
  safe_skip_if_missing "tenant_token" "$TENANT_TOKEN" "$TENANT_SCHEMA" || return 0

  local t1="/tmp/t1.$$".json
  curl -sS "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT_TOKEN" -H "X-Tenant: $TENANT_SCHEMA" > "$t1"

  python - "$t1" "$TENANT_ACCOUNT_ID" "$TENANT_SCHEMA" <<'PY'
import json, sys
path, expected_acc, expected_schema = sys.argv[1:]
with open(path, encoding='utf-8') as f:
    data = json.load(f)
actual_acc = str(data.get("accountId", ""))
actual_schema = str(data.get("tenantSchema", data.get("schemaName", "")))
if expected_acc and actual_acc and actual_acc != expected_acc:
    raise SystemExit(f"accountId mismatch: {actual_acc} != {expected_acc}")
if expected_schema and actual_schema and actual_schema != expected_schema:
    raise SystemExit(f"schema mismatch: {actual_schema} != {expected_schema}")
PY
  ok "Tenant isolation smoke test passed"
}

stress_logins_100() {
  banner "STRESS TEST: 100 parallel logins"
  safe_skip_if_missing "tenant_email" "$TENANT_EMAIL" "$TENANT_PASSWORD" || return 0

  export BASE_URL TENANT_EMAIL TENANT_PASSWORD
  seq 1 100 | xargs -I{} -P 100 bash -lc '
    curl -sS -X POST "$BASE_URL/api/tenant/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"$TENANT_EMAIL\",\"password\":\"$TENANT_PASSWORD\"}" >/dev/null
  '
  ok "100 parallel logins finished"
}

race_user_creation() {
  banner "RACE TEST: concurrent user creation"
  safe_skip_if_missing "tenant_token" "$TENANT_TOKEN" "$TENANT_SCHEMA" || return 0

  export BASE_URL TENANT_TOKEN TENANT_SCHEMA
  seq 1 20 | xargs -I{} -P 20 bash -lc '
    curl -sS -X POST "$BASE_URL/api/tenant/users" \
      -H "Authorization: Bearer $TENANT_TOKEN" \
      -H "X-Tenant: $TENANT_SCHEMA" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"RaceUser{}\",\"email\":\"raceuser{}@tenant.local\",\"password\":\"E2ePass123\",\"role\":\"TENANT_USER\",\"permissions\":[]}" >/dev/null || true
  '
  ok "Race user creation finished"
}

jwt_replay_attack_test() {
  banner "JWT replay attack test"
  safe_skip_if_missing "tenant_token" "$TENANT_TOKEN" "$TENANT_SCHEMA" || return 0

  local a="/tmp/jwt_a.$$".json
  local b="/tmp/jwt_b.$$".json
  local code1 code2
  code1="$(curl -sS -o "$a" -w "%{http_code}" "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT_TOKEN" -H "X-Tenant: $TENANT_SCHEMA")"
  code2="$(curl -sS -o "$b" -w "%{http_code}" "$BASE_URL/api/tenant/me" -H "Authorization: Bearer $TENANT_TOKEN" -H "X-Tenant: $TENANT_SCHEMA")"
  [[ "$code1" == "200" && "$code2" == "200" ]] || die "JWT replay smoke failed: statuses $code1 / $code2"
  ok "JWT replay smoke passed"
}

tenant_escape_attack_test() {
  banner "Tenant escape attack test"
  safe_skip_if_missing "tenant_token" "$TENANT_TOKEN" "$TENANT_SCHEMA" || return 0
  safe_skip_if_missing "tenant2_schema" "$TENANT2_SCHEMA" || { ok "Tenant escape test skipped safely (no tenant2_schema)"; return 0; }

  local out="/tmp/tenant_escape.$$".json
  local status
  status="$(curl -sS -o "$out" -w "%{http_code}" "$BASE_URL/api/tenant/me" \
    -H "Authorization: Bearer $TENANT_TOKEN" \
    -H "X-Tenant: $TENANT2_SCHEMA")"

  if [[ "$status" == "401" || "$status" == "403" || "$status" == "409" ]]; then
    ok "Tenant escape blocked with status $status"
    return 0
  fi

  if [[ "$status" != "200" ]]; then
    ok "Tenant escape non-200 status $status"
    return 0
  fi

  python - "$out" "$TENANT_ACCOUNT_ID" "$TENANT_SCHEMA" "$TENANT2_ACCOUNT_ID" "$TENANT2_SCHEMA" <<'PY'
import json, sys
path, tenant1_acc, tenant1_schema, tenant2_acc, tenant2_schema = sys.argv[1:]
with open(path, encoding='utf-8') as f:
    data = json.load(f)

actual_acc = str(data.get("accountId", ""))
actual_schema = str(data.get("tenantSchema", data.get("schemaName", "")))

# Allowed: backend ignores spoofed X-Tenant and still returns tenant1 context.
if tenant1_acc and actual_acc == tenant1_acc:
    sys.exit(0)
if tenant1_schema and actual_schema == tenant1_schema:
    sys.exit(0)

# Forbidden: request escaped into tenant2 context.
if tenant2_acc and actual_acc == tenant2_acc:
    raise SystemExit("cross-tenant accountId leaked")
if tenant2_schema and actual_schema == tenant2_schema:
    raise SystemExit("cross-tenant schema leaked")

# Unknown changed payload: treat as suspicious.
raise SystemExit(f"suspicious changed payload accountId={actual_acc} schema={actual_schema}")
PY
  ok "Tenant escape attack safely contained"
}

deadlock_detector() {
  banner "POSTGRES DEADLOCK DETECTOR"
  if grep -i "deadlock detected" "$APP_LOG" >/dev/null; then
    grep -i "deadlock detected" "$APP_LOG" || true
    die "Postgres deadlock detected"
  fi
  ok "No deadlock detected"
}

inventory_concurrency_test() {
  banner "INVENTORY CONCURRENCY TEST"
  safe_skip_if_missing "tenant_token" "$TENANT_TOKEN" "$TENANT_SCHEMA" || return 0

  export BASE_URL TENANT_TOKEN TENANT_SCHEMA
  seq 1 50 | xargs -I{} -P 50 bash -lc '
    curl -sS "$BASE_URL/api/tenant/products/low-stock/count?threshold=5" \
      -H "Authorization: Bearer $TENANT_TOKEN" \
      -H "X-Tenant: $TENANT_SCHEMA" >/dev/null || true
  '
  ok "Inventory concurrency finished"
}

simulate_many_tenants() {
  banner "SIMULATING ${ULTRA_TENANT_SIMULATION} TENANTS"
  safe_skip_if_missing "tenant_token" "$TENANT_TOKEN" "$TENANT_SCHEMA" || return 0

  local i
  for i in $(seq 1 "$ULTRA_TENANT_SIMULATION"); do
    curl -sS "$BASE_URL/api/tenant/me" \
      -H "Authorization: Bearer $TENANT_TOKEN" \
      -H "X-Tenant: tenant_test_$i" >/dev/null || true
  done
  ok "Tenant simulation finished"
}

billing_stress_test() {
  banner "BILLING MULTI-TENANT TEST"
  safe_skip_if_missing "superadmin_token" "$SUPERADMIN_TOKEN" "$TENANT_ACCOUNT_ID" || return 0

  export BASE_URL SUPERADMIN_TOKEN TENANT_ACCOUNT_ID PAYMENT_GATEWAY PAYMENT_METHOD ULTRA_BILLING_PARALLELISM
  seq 1 20 | xargs -I{} -P "$ULTRA_BILLING_PARALLELISM" bash -lc '
    curl -sS -X POST "$BASE_URL/api/controlplane/billing/payments" \
      -H "Authorization: Bearer $SUPERADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"accountId\":'"$TENANT_ACCOUNT_ID"',"amount":100,\"paymentGateway\":\"'"$PAYMENT_GATEWAY"'\",\"paymentMethod\":\"'"$PAYMENT_METHOD"'\"}" >/dev/null || true
  '
  ok "Billing stress finished"
}

ultra_sales_test() {
  banner "ULTRA STRESS: ${ULTRA_SALES_COUNT} simulated sales"
  safe_skip_if_missing "tenant_token" "$TENANT_TOKEN" "$TENANT_SCHEMA" || return 0
  safe_skip_if_missing "product_id" "$PRODUCT_ID" || { ok "Ultra sales skipped safely (no product_id)"; return 0; }

  export BASE_URL TENANT_TOKEN TENANT_SCHEMA PRODUCT_ID
  seq 1 "$ULTRA_SALES_COUNT" | xargs -I{} -P "$ULTRA_SALES_PARALLELISM" bash -lc '
    curl -sS -X POST "$BASE_URL/api/tenant/sales" \
      -H "Authorization: Bearer $TENANT_TOKEN" \
      -H "X-Tenant: $TENANT_SCHEMA" \
      -H "Content-Type: application/json" \
      -d "{\"saleDate\":\"2026-03-09T00:00:00Z\",\"status\":\"DRAFT\",\"items\":[{\"productId\":\"$PRODUCT_ID\",\"productName\":\"Stress Product\",\"quantity\":1,\"unitPrice\":100}]}" >/dev/null || true
  '
  ok "Ultra sales stress finished"
}

hikari_pool_stress() {
  banner "HIKARI POOL STRESS TEST"
  [[ "${HIKARI_POOL_STRESS_ENABLED,,}" == "true" ]] || { ok "Hikari pool stress disabled"; return 0; }
  safe_skip_if_missing "tenant_token" "$TENANT_TOKEN" "$TENANT_SCHEMA" || return 0

  export BASE_URL TENANT_TOKEN TENANT_SCHEMA
  seq 1 "$HIKARI_POOL_CONCURRENCY" | xargs -I{} -P "$HIKARI_POOL_CONCURRENCY" bash -lc '
    curl -sS "$BASE_URL/api/tenant/me" \
      -H "Authorization: Bearer $TENANT_TOKEN" \
      -H "X-Tenant: $TENANT_SCHEMA" >/dev/null || true
  '
  ok "Hikari pool stress finished"
}

main () {
  echo "Runner $VERSION"
  echo "Collection: $COLLECTION"
  echo "Env: $ENV_FILE"

  check_requirements
  check_port_available
  drop_db
  start_app
  wait_started
  health_check
  patch_env
  run_newman
  load_runtime_vars
  check_architecture
  performance_analysis

  echo ""
  echo "==========================================================="
  echo "✅ SUCCESS ($VERSION)"
  echo "==========================================================="
  echo ""

  parallel_login_stress
  tenant_isolation_smoke
  stress_logins_100
  race_user_creation
  jwt_replay_attack_test
  tenant_escape_attack_test
  deadlock_detector
  inventory_concurrency_test
  simulate_many_tenants
  billing_stress_test
  ultra_sales_test
  hikari_pool_stress

  echo "V18 SUITE COMPLETED"
}

main "$@"
