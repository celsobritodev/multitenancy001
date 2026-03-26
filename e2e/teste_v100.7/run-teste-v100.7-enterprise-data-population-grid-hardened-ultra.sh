#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export tenant_count="${tenant_count:-6}"
export users_per_tenant="${users_per_tenant:-10}"
export categories_per_tenant="${categories_per_tenant:-8}"
export subcategories_per_tenant="${subcategories_per_tenant:-12}"
export suppliers_per_tenant="${suppliers_per_tenant:-8}"
export customers_per_tenant="${customers_per_tenant:-20}"
export products_per_tenant="${products_per_tenant:-24}"
export sales_per_tenant="${sales_per_tenant:-40}"
export billings_per_account="${billings_per_account:-4}"
export max_sale_items="${max_sale_items:-5}"
if [[ -f "${SCRIPT_DIR}/run-teste-v100.7-enterprise-data-population-grid-hardened-strict.sh" ]]; then
  bash "${SCRIPT_DIR}/run-teste-v100.7-enterprise-data-population-grid-hardened-strict.sh"
else
  bash "${SCRIPT_DIR}/run-teste-v100-heavy-data-population-grid-strict.sh"
fi
