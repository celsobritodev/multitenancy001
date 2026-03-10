#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Runner: Enterprise E2E Reset Runner
# Version: v6.9.3.3
# Name: ENTERPRISE-CUSTOMERS-SALES-CONSOLIDATED
# ==============================================================================

clear || true
pwd_path="$(pwd)"
echo "📁 Execution path: ${pwd_path}"

SCRIPT_VERSION="v6.9.3.3"
RUNNER_NAME="ENTERPRISE-CUSTOMERS-SALES-CONSOLIDATED"

COLLECTION="${COLLECTION:-e2e/multitenancy001.postman_collection.v12.0.enterprise.json}"
ENV_FILE="${ENV_FILE:-e2e/multitenancy001.local.postman_environment.v8.0.json}"

PATCHED_COLLECTION=".e2e-${SCRIPT_VERSION}.enterprise.patched.collection.json"
NEWMAN_JSON=".e2e-${SCRIPT_VERSION}.newman.json"
PATCH_LOG=".e2e-${SCRIPT_VERSION}.patch.log"
APP_LOG=".e2e-app.log"
EFFECTIVE_ENV=".env.effective.json"

BASE_URL="${BASE_URL:-http://localhost:8080}"

DB_NAME="${DB_NAME:-db_multitenancy}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-admin}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

APP_PID=""
APP_START_CMD=""

NEWMAN_REQUESTS="N/A"
NEWMAN_ASSERTIONS="N/A"
NEWMAN_FAILURES="N/A"
NEWMAN_DURATION_MS="N/A"

print_step()    { echo "==> $1"; }
print_success() { echo "✅ $1"; }
print_warn()    { echo "⚠ $1"; }
print_error()   { echo "❌ $1"; }

print_header() {
  echo "========================================="
  echo "Runner ${SCRIPT_VERSION}-${RUNNER_NAME}"
  echo "========================================="
  echo "Collection: ${COLLECTION}"
  echo "Env: ${ENV_FILE}"
  echo
}

stop_app() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" >/dev/null 2>&1; then
    echo
    print_step "Stop app (pid=${APP_PID})"
    kill "${APP_PID}" >/dev/null 2>&1 || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
}

trap stop_app EXIT

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    print_error "Command not found: $1"
    exit 1
  fi
}

choose_app_start() {
  if [[ -x "./mvnw" ]]; then
    APP_START_CMD="./mvnw spring-boot:run"
  else
    require_cmd mvn
    APP_START_CMD="mvn spring-boot:run"
  fi
}

ensure_port_free() {
  print_step "Checking if port 8080 is available"

  local pids=""
  if command -v netstat >/dev/null 2>&1; then
    pids="$(netstat -ano | grep ':8080' | grep LISTENING | awk '{print $NF}' | sort -u || true)"
  fi

  if [[ -n "${pids}" ]]; then
    print_warn "Port 8080 is in use. Attempting to kill process(es) with Windows taskkill..."
    echo "Found Windows PID(s) on port 8080: ${pids}"
    for pid in ${pids}; do
      echo "Killing Windows process ${pid} (attempt 1)..."
      taskkill //PID "${pid}" //F || true
    done
    sleep 2
    print_success "Port 8080 is now free"
  else
    print_success "Port 8080 available"
  fi
}

reset_database() {
  print_step "Drop DB (${DB_NAME})"

  PGPASSWORD="${DB_PASS}" psql \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d postgres <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname='${DB_NAME}';

DROP DATABASE IF EXISTS ${DB_NAME};
CREATE DATABASE ${DB_NAME};
SQL

  print_success "Database reset completed"
}

start_app() {
  print_step "Start app"
  choose_app_start
  echo "Using start command: ${APP_START_CMD}"
  bash -lc "${APP_START_CMD}" > "${APP_LOG}" 2>&1 &
  APP_PID=$!
  echo "App PID=${APP_PID}"
}

wait_for_app() {
  print_step "Waiting application start"

  for _ in {1..180}; do
    if grep -q "Started .*Application" "${APP_LOG}" 2>/dev/null; then
      echo "Application STARTED"
      break
    fi
    sleep 2
  done

  print_step "Health check ${BASE_URL}/actuator/health"
  for _ in {1..60}; do
    if curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
      echo "Health OK"
      return
    fi
    sleep 2
  done

  print_error "Health check failed"
  exit 1
}

prepare_env() {
  print_step "Prepare effective env"
  cp "${ENV_FILE}" "${EFFECTIVE_ENV}"
  echo "Env ready -> $(pwd)/${EFFECTIVE_ENV}"
}

patch_collection() {
  print_step "Patching collection for customer->sale linkage (${SCRIPT_VERSION})"

  local py=""
  if command -v python3 >/dev/null 2>&1; then
    py="python3"
  elif command -v python >/dev/null 2>&1; then
    py="python"
  else
    print_error "Python not installed"
    exit 1
  fi

  rm -f "${PATCH_LOG}"

  COLLECTION_PATH="${COLLECTION}" PATCHED_PATH="${PATCHED_COLLECTION}" "$py" >"${PATCH_LOG}" 2>&1 <<'PY'
import json
import os
import re
import sys
from copy import deepcopy
from pathlib import Path

collection_path = Path(os.environ["COLLECTION_PATH"])
patched_path = Path(os.environ["PATCHED_PATH"])

if not collection_path.exists():
    raise SystemExit(f"Collection not found: {collection_path}")

data = json.loads(collection_path.read_text(encoding="utf-8"))

customer_ids = [
    "{{mass_customer_id_01}}",
    "{{mass_customer_id_02}}",
    "{{mass_customer_id_03}}",
    "{{mass_customer_id_04}}",
    "{{mass_customer_id_05}}",
    "{{mass_customer_id_06}}",
    "{{mass_customer_id_07}}",
    "{{mass_customer_id_08}}",
    "{{mass_customer_id_09}}",
    "{{mass_customer_id_10}}",
]

def script_lines(script):
    if not script:
        return []
    if isinstance(script, dict):
        exec_part = script.get("exec", [])
    else:
        exec_part = []
    if isinstance(exec_part, list):
        return exec_part
    if isinstance(exec_part, str):
        return exec_part.splitlines()
    return []

def set_script_lines(script, lines):
    if not script:
        return
    if isinstance(script, dict):
        script["exec"] = lines

def patch_sale_request(item, sale_index):
    body = item.get("request", {}).get("body", {})
    raw = body.get("raw")
    if isinstance(raw, str) and '"customerId"' in raw:
        raw = re.sub(
            r'"customerId"\s*:\s*"[^"]*"',
            f'"customerId": "{customer_ids[sale_index]}"',
            raw
        )
        body["raw"] = raw

    events = item.get("event", [])
    for ev in events:
        listen = ev.get("listen")
        lines = script_lines(ev.get("script"))

        if listen == "prerequest":
            joined = "\n".join(lines)
            if "pm.info.requestName" in joined or "customerId" in joined:
                new_lines = [
                    "const customerIds = [",
                    "  pm.collectionVariables.get('mass_customer_id_01'),",
                    "  pm.collectionVariables.get('mass_customer_id_02'),",
                    "  pm.collectionVariables.get('mass_customer_id_03'),",
                    "  pm.collectionVariables.get('mass_customer_id_04'),",
                    "  pm.collectionVariables.get('mass_customer_id_05'),",
                    "  pm.collectionVariables.get('mass_customer_id_06'),",
                    "  pm.collectionVariables.get('mass_customer_id_07'),",
                    "  pm.collectionVariables.get('mass_customer_id_08'),",
                    "  pm.collectionVariables.get('mass_customer_id_09'),",
                    "  pm.collectionVariables.get('mass_customer_id_10')",
                    "].filter(Boolean);",
                    f"const selectedCustomerId = customerIds[{sale_index}] || customerIds[0] || null;",
                    "let payload = {};",
                    "try { payload = JSON.parse(pm.request.body.raw || '{}'); } catch (e) { payload = {}; }",
                    "payload.customerId = selectedCustomerId;",
                    "pm.request.body.update(JSON.stringify(payload, null, 2));",
                    "console.log('📊 customerId:', selectedCustomerId);",
                ]
                set_script_lines(ev.get("script"), new_lines)

def walk_items(items):
    for item in items:
        name = item.get("name", "")
        if "item" in item:
            walk_items(item["item"])
        else:
            m = re.search(r"Criar venda \((\d+)/10\)", name)
            if m:
                idx = int(m.group(1)) - 1
                patch_sale_request(item, idx)

walk_items(data.get("item", []))
patched_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
print("PATCH_OK")
PY

  if [[ $? -ne 0 ]]; then
    print_error "Collection patch failed"
    if [[ -s "${PATCH_LOG}" ]]; then
      print_warn "Patch error details:"
      cat "${PATCH_LOG}"
    else
      print_warn "Patch error details: <empty log>"
    fi
    exit 1
  fi

  if ! grep -q "PATCH_OK" "${PATCH_LOG}" 2>/dev/null; then
    print_error "Collection patch failed"
    if [[ -s "${PATCH_LOG}" ]]; then
      print_warn "Patch error details:"
      cat "${PATCH_LOG}"
    else
      print_warn "Patch error details: <empty log>"
    fi
    exit 1
  fi

  print_success "Patched collection ready -> $(pwd)/${PATCHED_COLLECTION}"
}

run_newman() {
  print_step "Run Newman"
  newman run "${PATCHED_COLLECTION}" \
    -e "${EFFECTIVE_ENV}" \
    -r cli,json \
    --reporter-json-export "${NEWMAN_JSON}"
}

parse_metrics() {
  if [[ ! -f "${NEWMAN_JSON}" ]]; then
    print_warn "Newman report not found"
    return
  fi

  local py=""
  if command -v python3 >/dev/null 2>&1; then
    py="python3"
  else
    py="python"
  fi

  mapfile -t lines < <(NEWMAN_JSON_PATH="${NEWMAN_JSON}" "$py" <<'PY'
import json
import os
from pathlib import Path

p = Path(os.environ["NEWMAN_JSON_PATH"])
data = json.loads(p.read_text(encoding="utf-8"))
run = data.get("run", {})
stats = run.get("stats", {})
timings = run.get("timings", {})
failures = run.get("failures", [])

print(stats.get("requests", {}).get("total", "N/A"))
print(stats.get("assertions", {}).get("total", "N/A"))
print(len(failures))
print(timings.get("completed", "N/A"))
PY
)

  NEWMAN_REQUESTS="${lines[0]:-N/A}"
  NEWMAN_ASSERTIONS="${lines[1]:-N/A}"
  NEWMAN_FAILURES="${lines[2]:-N/A}"
  NEWMAN_DURATION_MS="${lines[3]:-N/A}"
}

print_summary() {
  echo
  echo "==========================================================="
  echo "✅ NEWMAN SUCCESS (${SCRIPT_VERSION}-${RUNNER_NAME})"
  echo "==========================================================="
  echo
  echo "📌 EXECUTIVE SUMMARY"
  echo "Runner: ${RUNNER_NAME}"
  echo "Requests: ${NEWMAN_REQUESTS}"
  echo "Assertions: ${NEWMAN_ASSERTIONS}"
  echo "Failures: ${NEWMAN_FAILURES}"
  echo "Tempo total (ms): ${NEWMAN_DURATION_MS}"
  echo
  echo "========================================="
  echo "✅ ALL TESTS COMPLETED"
  echo "========================================="
}

main() {
  require_cmd curl
  require_cmd psql
  require_cmd newman

  print_header
  ensure_port_free
  reset_database
  start_app
  wait_for_app
  prepare_env
  patch_collection
  run_newman
  parse_metrics
  print_summary
}

main "$@"
