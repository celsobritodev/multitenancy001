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

attempt=0
while :; do
  attempt=$((attempt+1))
  jitter=$(rand_ms "${MAX_JITTER_MS}")
  total_delay=$(( BASE_DELAY_MS + jitter ))
  sleep_ms "${total_delay}"

  code=$(curl -sS -o /tmp/chaos-race-body.$$ -w "%{http_code}" \
    -X POST "${BASE_URL}/api/tenant/sales" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "X-Tenant: ${SCHEMA}" \
    -H "Content-Type: application/json" \
    --data "{\"customerId\":\"${CUSTOMER_ID}\",\"saleDate\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"status\":\"DRAFT\",\"items\":[{\"productId\":\"${PRODUCT_ID}\",\"productName\":\"${PRODUCT_NAME}\",\"quantity\":1,\"unitPrice\":${UNIT_PRICE}}]}")

  if [[ "${code}" == "200" || "${code}" == "201" ]]; then
    echo "${code}"
    exit 0
  fi

  if [[ "$attempt" -gt "${RETRY_COUNT}" ]]; then
    echo "${code}"
    exit 0
  fi
done
