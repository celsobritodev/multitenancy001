#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="$1"
TOKEN="$2"
SCHEMA="$3"
CUSTOMER_ID="$4"
PRODUCT_ID="$5"
UNIT_PRICE="$6"
PRODUCT_NAME="$7"
MAX_JITTER_MS="$8"
BASE_DELAY_MS="$9"
RETRY_COUNT="${10}"
SALE_STATUS="${11:-OPEN}"
WORKER_INDEX="${12:-0}"
WORKER_LOG_DIR="${13:-/tmp}"
CHAOS_RUN_ID="${14:-chaos-run}"

rand_ms() {
  local max="$1"
  if [[ "$max" -le 0 ]]; then echo 0; return; fi
  echo $(( RANDOM % (max + 1) ))
}

sleep_ms() {
  local ms="$1"
  python - "$ms" <<'PY'
import sys, time
try:
    time.sleep(int(sys.argv[1]) / 1000.0)
except Exception:
    pass
PY
}

is_inventory_affecting_status() {
  case "$1" in
    OPEN|CONFIRMED|PAID) echo 1 ;;
    *) echo 0 ;;
  esac
}

json_escape() {
  python - "$1" <<'PY'
import json, sys
print(json.dumps(sys.argv[1]))
PY
}

extract_json_field() {
  local body_file="$1"
  local field="$2"
  python - "$body_file" "$field" <<'PY'
import json, sys
path, field = sys.argv[1], sys.argv[2]
try:
    with open(path, 'r', encoding='utf-8', errors='ignore') as fh:
        data = json.load(fh)
    value = data.get(field, '') if isinstance(data, dict) else ''
    print('' if value is None else value)
except Exception:
    print('')
PY
}

monotonic_ns() {
  python - <<'PY'
import time
print(time.monotonic_ns())
PY
}

epoch_ms() {
  python - <<'PY'
import time
print(int(time.time() * 1000))
PY
}

mkdir -p "$WORKER_LOG_DIR"
AFFECTS_INVENTORY="$(is_inventory_affecting_status "$SALE_STATUS")"
ATTEMPT_LOG="$WORKER_LOG_DIR/worker_${WORKER_INDEX}.log"
FINAL_TSV="$WORKER_LOG_DIR/worker_${WORKER_INDEX}.final.tsv"
: > "$ATTEMPT_LOG"
: > "$FINAL_TSV"

worker_start_monotonic_ns="$(monotonic_ns)"
worker_start_epoch_ms="$(epoch_ms)"
CORRELATION_ROOT="${CHAOS_RUN_ID}-w${WORKER_INDEX}"

declare -a TRACE_CODES=()
declare -a TRACE_API_CODES=()
declare -a TRACE_LATENCIES=()
declare -a TRACE_CORRELATIONS=()

printf 'WORKER_START workerIndex=%s chaosRunId=%s workerStartMonotonicNs=%s workerStartEpochMs=%s status=%s affectsInventory=%s correlationRoot=%s\n' \
  "$WORKER_INDEX" "$CHAOS_RUN_ID" "$worker_start_monotonic_ns" "$worker_start_epoch_ms" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$CORRELATION_ROOT" >> "$ATTEMPT_LOG"

attempt=0
while :; do
  attempt=$((attempt+1))
  jitter=$(rand_ms "${MAX_JITTER_MS}")
  total_delay=$(( BASE_DELAY_MS + jitter ))
  correlation_id="${CORRELATION_ROOT}-a${attempt}"
  retry_phase="$( [[ "$attempt" -le 1 ]] && echo initial || echo retry )"

  printf 'RETRY_TRACE workerIndex=%s attempt=%s retryPhase=%s correlationId=%s scheduledDelayMs=%s jitterMs=%s baseDelayMs=%s\n' \
    "$WORKER_INDEX" "$attempt" "$retry_phase" "$correlation_id" "$total_delay" "$jitter" "$BASE_DELAY_MS" >> "$ATTEMPT_LOG"

  sleep_ms "${total_delay}"

  sale_date="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  req_file="$(mktemp)"
  hdr_file="$(mktemp)"
  body_file="$(mktemp)"
  time_file="$(mktemp)"
  monotonic_start_ns="$(monotonic_ns)"
  epoch_start_ms="$(epoch_ms)"

  printf '{"customerId":"%s","saleDate":"%s","status":"%s","items":[{"productId":"%s","productName":%s,"quantity":1,"unitPrice":%s}]}' \
    "$CUSTOMER_ID" "$sale_date" "$SALE_STATUS" "$PRODUCT_ID" "$(json_escape "$PRODUCT_NAME")" "$UNIT_PRICE" > "$req_file"

  curl -sS -o "$body_file" -D "$hdr_file" -w "%{time_total}" \
    -X POST "${BASE_URL}/api/tenant/sales" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "X-Tenant: ${SCHEMA}" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-Id: ${correlation_id}" \
    --data @"$req_file" > "$time_file"

  time_total="$(cat "$time_file" 2>/dev/null || echo 0)"
  code="$(awk 'toupper($1) ~ /^HTTP\// {c=$2} END{print c+0}' "$hdr_file")"
  [[ -n "$code" ]] || code=0
  elapsed_ms=$(python - "$time_total" <<'PY'
import sys
try:
    print(int(round(float(sys.argv[1]) * 1000)))
except Exception:
    print(0)
PY
)
  monotonic_end_ns="$(monotonic_ns)"
  epoch_end_ms="$(epoch_ms)"
  monotonic_elapsed_ms=$(python - "$monotonic_start_ns" "$monotonic_end_ns" <<'PY'
import sys
try:
    start = int(sys.argv[1]); end = int(sys.argv[2])
    print(int(round((end - start) / 1_000_000)))
except Exception:
    print(0)
PY
)
  sale_id="$(extract_json_field "$body_file" id)"
  [[ -n "$sale_id" ]] || sale_id="$(extract_json_field "$body_file" saleId)"
  api_code="$(extract_json_field "$body_file" code)"
  compact_body="$(tr -d '\n\r' < "$body_file" | cut -c1-400)"

  TRACE_CODES+=("$code")
  TRACE_API_CODES+=("${api_code:-NA}")
  TRACE_LATENCIES+=("$monotonic_elapsed_ms")
  TRACE_CORRELATIONS+=("$correlation_id")

  printf 'ATTEMPT_RESULT workerIndex=%s attempt=%s retryPhase=%s correlationId=%s code=%s apiCode=%s status=%s affectsInventory=%s elapsedMs=%s monotonicElapsedMs=%s monotonicStartNs=%s monotonicEndNs=%s epochStartMs=%s epochEndMs=%s saleId=%s body=%s\n' \
    "$WORKER_INDEX" "$attempt" "$retry_phase" "$correlation_id" "$code" "$api_code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$elapsed_ms" "$monotonic_elapsed_ms" "$monotonic_start_ns" "$monotonic_end_ns" "$epoch_start_ms" "$epoch_end_ms" "$sale_id" "$compact_body" >> "$ATTEMPT_LOG"

  if [[ "$code" == "200" || "$code" == "201" || "$attempt" -gt "$RETRY_COUNT" ]]; then
    retry_trace="$(IFS=,; echo "${TRACE_CODES[*]}")"
    retry_api_trace="$(IFS=,; echo "${TRACE_API_CODES[*]}")"
    latency_trace_ms="$(IFS=,; echo "${TRACE_LATENCIES[*]}")"
    correlation_trace="$(IFS=,; echo "${TRACE_CORRELATIONS[*]}")"
    retry_used=$(( attempt - 1 ))
    worker_end_monotonic_ns="$(monotonic_ns)"
    worker_end_epoch_ms="$(epoch_ms)"
    worker_total_monotonic_ms=$(python - "$worker_start_monotonic_ns" "$worker_end_monotonic_ns" <<'PY'
import sys
try:
    start = int(sys.argv[1]); end = int(sys.argv[2])
    print(int(round((end - start) / 1_000_000)))
except Exception:
    print(0)
PY
)

    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$code" "$api_code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$sale_id" "$elapsed_ms" "$correlation_id" "$retry_used" "$monotonic_elapsed_ms" "$worker_total_monotonic_ms" "$retry_trace" "$latency_trace_ms" "$correlation_trace" > "$FINAL_TSV"

    printf 'FINAL_RESULT workerIndex=%s correlationId=%s attempt=%s retryUsed=%s code=%s apiCode=%s status=%s affectsInventory=%s elapsedMs=%s monotonicElapsedMs=%s monotonicStartNs=%s monotonicEndNs=%s workerTotalMonotonicMs=%s workerStartMonotonicNs=%s workerEndMonotonicNs=%s epochStartMs=%s epochEndMs=%s saleId=%s retryTrace=%s retryApiTrace=%s latencyTraceMs=%s correlationTrace=%s\n' \
      "$WORKER_INDEX" "$correlation_id" "$attempt" "$retry_used" "$code" "$api_code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$elapsed_ms" "$monotonic_elapsed_ms" "$monotonic_start_ns" "$monotonic_end_ns" "$worker_total_monotonic_ms" "$worker_start_monotonic_ns" "$worker_end_monotonic_ns" "$epoch_start_ms" "$epoch_end_ms" "$sale_id" "$retry_trace" "$retry_api_trace" "$latency_trace_ms" "$correlation_trace" >> "$ATTEMPT_LOG"

    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$code" "$api_code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$sale_id" "$elapsed_ms" "$correlation_id" "$retry_used" "$monotonic_elapsed_ms" "$worker_total_monotonic_ms" "$retry_trace" "$latency_trace_ms" "$correlation_trace"
    rm -f "$req_file" "$hdr_file" "$body_file" "$time_file"
    exit 0
  fi

  rm -f "$req_file" "$hdr_file" "$body_file" "$time_file"
done
