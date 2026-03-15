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

rand_ms() {
  local max="$1"
  if [[ "$max" -le 0 ]]; then echo 0; return; fi
  echo $(( RANDOM % (max + 1) ))
}

sleep_ms() {
  local ms="$1"
  python - "$ms" <<'PY'
import sys, time
time.sleep(int(sys.argv[1])/1000.0)
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

mkdir -p "$WORKER_LOG_DIR"
AFFECTS_INVENTORY="$(is_inventory_affecting_status "$SALE_STATUS")"
ATTEMPT_LOG="$WORKER_LOG_DIR/worker_${WORKER_INDEX}.log"
: > "$ATTEMPT_LOG"

attempt=0
while :; do
  attempt=$((attempt+1))
  jitter=$(rand_ms "${MAX_JITTER_MS}")
  total_delay=$(( BASE_DELAY_MS + jitter ))
  sleep_ms "${total_delay}"

  sale_date="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  req_file="$(mktemp)"
  resp_file="$(mktemp)"
  body_file="$(mktemp)"

  printf '{"customerId":"%s","saleDate":"%s","status":"%s","items":[{"productId":"%s","productName":%s,"quantity":1,"unitPrice":%s}]}' \
    "$CUSTOMER_ID" "$sale_date" "$SALE_STATUS" "$PRODUCT_ID" "$(json_escape "$PRODUCT_NAME")" "$UNIT_PRICE" > "$req_file"

  curl -sS -o "$body_file" -D "$resp_file" -w "%{time_total}" \
    -X POST "${BASE_URL}/api/tenant/sales" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "X-Tenant: ${SCHEMA}" \
    -H "Content-Type: application/json" \
    --data @"$req_file" > "${resp_file}.time"

  time_total="$(cat "${resp_file}.time" 2>/dev/null || echo 0)"
  code="$(awk 'toupper($1) ~ /^HTTP\// {c=$2} END{print c+0}' "$resp_file")"
  elapsed_ms=$(python - "$time_total" <<'PY'
import sys
try:
    print(int(round(float(sys.argv[1])*1000)))
except Exception:
    print(0)
PY
)
  sale_id="$(python - "$body_file" <<'PY'
import json, sys
try:
    data = json.load(open(sys.argv[1], 'r', encoding='utf-8'))
    print(data.get('id') or data.get('saleId') or '')
except Exception:
    print('')
PY
)"
  api_code="$(python - "$body_file" <<'PY'
import json, sys
try:
    data = json.load(open(sys.argv[1], 'r', encoding='utf-8'))
    print(data.get('code') or '')
except Exception:
    print('')
PY
)"
  compact_body="$(tr -d '\n\r' < "$body_file" | cut -c1-400)"
  printf 'attempt=%s code=%s apiCode=%s status=%s affectsInventory=%s elapsedMs=%s saleId=%s body=%s\n' \
    "$attempt" "$code" "$api_code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$elapsed_ms" "$sale_id" "$compact_body" >> "$ATTEMPT_LOG"

  rm -f "$req_file" "$resp_file" "${resp_file}.time"

  if [[ "$code" == "200" || "$code" == "201" ]]; then
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$code" "$api_code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$sale_id" "$elapsed_ms"
    rm -f "$body_file"
    exit 0
  fi

  if [[ "$attempt" -gt "$RETRY_COUNT" ]]; then
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$code" "$api_code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$sale_id" "$elapsed_ms"
    rm -f "$body_file"
    exit 0
  fi

  rm -f "$body_file"
done
