#!/bin/bash
set -euo pipefail

VERSION="v17-ULTRA-OBSERVABILITY"

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
NEWMAN_OUT="${NEWMAN_OUT:-$PROJECT_DIR/.e2e-newman.out.log}"
NEWMAN_JSON="${NEWMAN_JSON:-$PROJECT_DIR/.e2e-newman.report.json}"

PERF_RANKING="${PERF_RANKING:-$PROJECT_DIR/.e2e-performance-ranking.txt}"
PERF_MODULES="${PERF_MODULES:-$PROJECT_DIR/.e2e-performance-modules.txt}"
PERF_HISTORY="${PERF_HISTORY:-$PROJECT_DIR/e2e/.perf-history.csv}"

APP_PID=""

banner () { echo "==> $1"; }

die () {
  echo ""
  echo "==========================================================="
  echo "❌ $1"
  echo "==========================================================="
  echo ""
  exit 1
}

cleanup () {
  if [[ -n "${APP_PID:-}" ]]; then
    echo "==> Stop app (pid=$APP_PID)"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

check_port_available() {
  banner "Checking port $APP_PORT"
  if command -v lsof >/dev/null 2>&1; then
    if lsof -i :"$APP_PORT" >/dev/null 2>&1; then
      die "Port $APP_PORT already in use"
    fi
  fi
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
  banner "Start application"
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
      tail -n 200 "$APP_LOG"
      die "Application did not start"
    fi
    sleep 1
  done
}

health_check () {
  banner "Health check"
  local url="http://localhost:${APP_PORT}${APP_HEALTH_PATH}"
  for i in {1..30}; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "Health OK"
      return
    fi
    sleep 1
  done
  die "Health check failed"
}

run_newman () {
  banner "Run Newman"
  command -v newman >/dev/null 2>&1 || die "newman not installed"

  : > "$NEWMAN_OUT"

  set +e
  newman run "$PROJECT_DIR/$COLLECTION" \
    -e "$PROJECT_DIR/$ENV_FILE" \
    --timeout-request 30000 \
    --timeout-script 30000 \
    --reporters cli,json \
    --reporter-json-export "$NEWMAN_JSON" \
    --insecure \
    2>&1 | tee -a "$NEWMAN_OUT"
  status=${PIPESTATUS[0]}
  set -e

  if [[ $status -ne 0 ]]; then
    echo "Newman failed"
    tail -n 200 "$APP_LOG"
    exit $status
  fi
}

performance_analysis () {

  banner "Performance analysis"

  if ! command -v jq >/dev/null 2>&1; then
    echo "jq not installed; skipping observability"
    return
  fi

  jq -r '.run.executions[] | select(.response != null) | "\(.item.name)|\(.response.responseTime)"' "$NEWMAN_JSON" \
    | sort -t'|' -k2 -nr \
    | head -n 20 \
    | awk -F'|' '{printf "%-70s %8s ms\n",$1,$2}' \
    > "$PERF_RANKING"

  jq -r '.run.executions[] | select(.response != null) | "\(.item.name)|\(.response.responseTime)"' "$NEWMAN_JSON" \
    | awk -F'|' '
      {
        name=$1
        t=$2+0
        if (name ~ /auth/i) m="AUTH"
        else if (name ~ /product/i) m="PRODUCTS"
        else if (name ~ /sale/i) m="SALES"
        else if (name ~ /billing/i) m="BILLING"
        else m="OTHER"
        count[m]++
        sum[m]+=t
        vals[m,count[m]]=t
      }
      END {
        for (m in count) {
          n=count[m]
          for(i=1;i<=n;i++) arr[i]=vals[m,i]
          asort(arr)
          avg=sum[m]/n
          p95=arr[int(n*0.95)]
          printf "%-15s requests=%4d avg_ms=%8.2f p95_ms=%8.2f\n",m,n,avg,p95
          delete arr
        }
      }' \
    > "$PERF_MODULES"

  requests=$(jq '.run.stats.requests.total' "$NEWMAN_JSON")
  failures=$(jq '.run.stats.requests.failed' "$NEWMAN_JSON")
  avg=$(jq '[.run.executions[] | select(.response != null) | .response.responseTime] | add/length' "$NEWMAN_JSON")
  p95=$(jq '[.run.executions[] | select(.response != null) | .response.responseTime] | sort | .[(length*0.95|floor)]' "$NEWMAN_JSON")
  p99=$(jq '[.run.executions[] | select(.response != null) | .response.responseTime] | sort | .[(length*0.99|floor)]' "$NEWMAN_JSON")
  max=$(jq '[.run.executions[] | select(.response != null) | .response.responseTime] | max' "$NEWMAN_JSON")

  mkdir -p "$(dirname "$PERF_HISTORY")"

  if [[ ! -f "$PERF_HISTORY" ]]; then
    echo "timestamp,requests,failures,avg_ms,p95_ms,p99_ms,max_ms" > "$PERF_HISTORY"
  fi

  echo "$(date -Iseconds),$requests,$failures,$avg,$p95,$p99,$max" >> "$PERF_HISTORY"

  echo ""
  echo "==========================================================="
  echo "V17 ULTRA OBSERVABILITY - PERFORMANCE REPORT"
  echo "==========================================================="
  echo ""
  echo "requests_total: $requests"
  echo "failures_total: $failures"
  printf "latency_avg_ms: %.2f\n" "$avg"
  printf "latency_p95_ms: %.2f\n" "$p95"
  printf "latency_p99_ms: %.2f\n" "$p99"
  printf "latency_max_ms: %.2f\n" "$max"
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
}

main () {

  echo "Runner $VERSION"
  echo "Collection: $COLLECTION"
  echo "Env: $ENV_FILE"

  check_port_available
  drop_db
  start_app
  wait_started
  health_check
  run_newman
  performance_analysis

  echo ""
  echo "==========================================================="
  echo "SUCCESS ($VERSION)"
  echo "==========================================================="
  echo ""
}

main "$@"
