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
ms=int(sys.argv[1])
time.sleep(ms/1000.0)
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
  body_file="$(mktemp)"

  printf '{"customerId":"%s","saleDate":"%s","status":"%s","items":[{"productId":"%s","productName":%s,"quantity":1,"unitPrice":%s}]}' \
    "$CUSTOMER_ID" "$sale_date" "$SALE_STATUS" "$PRODUCT_ID" "$(json_escape "$PRODUCT_NAME")" "$UNIT_PRICE" > "$req_file"

  code=$(curl -sS -o "$body_file" -w "%{http_code}" \
    -X POST "${BASE_URL}/api/tenant/sales" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "X-Tenant: ${SCHEMA}" \
    -H "Content-Type: application/json" \
    --data @"$req_file")

  sale_id=""
  if [[ -s "$body_file" ]]; then
    sale_id="$(python - "$body_file" <<'PY'
import json, sys
try:
    with open(sys.argv[1], 'r', encoding='utf-8') as f:
        data = json.load(f)
    value = data.get('id') or data.get('saleId') or ''
    print(value)
except Exception:
    print('')
PY
)"
  fi

  compact_body="$(tr -d '\n\r' < "$body_file" | cut -c1-400)"
  printf 'attempt=%s code=%s status=%s affectsInventory=%s saleId=%s body=%s\n' \
    "$attempt" "$code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$sale_id" "$compact_body" >> "$ATTEMPT_LOG"

  rm -f "$req_file"

  if [[ "$code" == "200" || "$code" == "201" ]]; then
    printf '%s\t%s\t%s\t%s\n' "$code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$sale_id"
    rm -f "$body_file"
    exit 0
  fi

  if [[ "$attempt" -gt "$RETRY_COUNT" ]]; then
    printf '%s\t%s\t%s\t%s\n' "$code" "$SALE_STATUS" "$AFFECTS_INVENTORY" "$sale_id"
    rm -f "$body_file"
    exit 0
  fi

  rm -f "$body_file"
done
