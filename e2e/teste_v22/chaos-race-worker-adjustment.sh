#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="$1"
TOKEN="$2"
SCHEMA="$3"
PRODUCT_ID="$4"
QUANTITY="${5:-1}"
MAX_JITTER_MS="${6:-0}"
BASE_DELAY_MS="${7:-0}"
MOVEMENT_TYPE="${8:-INBOUND}"
WORKER_INDEX="${9:-0}"
WORKER_LOG_DIR="${10:-/tmp}"

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

mkdir -p "$WORKER_LOG_DIR"
ATTEMPT_LOG="$WORKER_LOG_DIR/adjust_worker_${WORKER_INDEX}.log"
: > "$ATTEMPT_LOG"

jitter=$(rand_ms "${MAX_JITTER_MS}")
total_delay=$(( BASE_DELAY_MS + jitter ))
sleep_ms "${total_delay}"

ref_id="v22-adjust-${WORKER_INDEX}-$(date +%s)"
body_file="$(mktemp)"

code=$(curl -sS -o "$body_file" -w "%{http_code}" \
  -X POST "${BASE_URL}/api/tenant/inventory/adjustments" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Tenant: ${SCHEMA}" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":\"${PRODUCT_ID}\",\"quantity\":${QUANTITY},\"movementType\":\"${MOVEMENT_TYPE}\",\"referenceType\":\"MANUAL_ADJUSTMENT\",\"referenceId\":\"${ref_id}\",\"notes\":\"V22 adjustment worker\"}")

compact_body="$(tr -d '\n\r' < "$body_file" | cut -c1-400)"
printf 'code=%s movementType=%s referenceId=%s body=%s\n' \
  "$code" "$MOVEMENT_TYPE" "$ref_id" "$compact_body" >> "$ATTEMPT_LOG"

printf '%s\t%s\t%s\n' "$code" "$MOVEMENT_TYPE" "$ref_id"
rm -f "$body_file"
