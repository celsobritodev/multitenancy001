#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="$1"
TOKEN="$2"
SCHEMA="$3"
CUSTOMER_ID="$4"
PRODUCT_ID="$5"
UNIT_PRICE="$6"
PRODUCT_NAME="$7"

curl -sS -o /tmp/race-worker-body.$$ -w "%{http_code}" \
  -X POST "${BASE_URL}/api/tenant/sales" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Tenant: ${SCHEMA}" \
  -H "Content-Type: application/json" \
  --data "{\"customerId\":\"${CUSTOMER_ID}\",\"saleDate\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"status\":\"DRAFT\",\"items\":[{\"productId\":\"${PRODUCT_ID}\",\"productName\":\"${PRODUCT_NAME}\",\"quantity\":1,\"unitPrice\":${UNIT_PRICE}}]}"
