#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "────────────────────────────────────────────────────"
echo "🔷 CLEANUP V29"
echo "────────────────────────────────────────────────────"

rm -f "${SCRIPT_DIR}/.env.effective.json" >/dev/null 2>&1 || true
rm -f "${SCRIPT_DIR}/.newman-report.strict.json" >/dev/null 2>&1 || true
rm -f "${SCRIPT_DIR}/.newman-report.ultra.json" >/dev/null 2>&1 || true
rm -f "${SCRIPT_DIR}/.app.pid" >/dev/null 2>&1 || true

mkdir -p "${SCRIPT_DIR}/logs"

if [[ -L "${SCRIPT_DIR}/mvnw" ]]; then
  rm -f "${SCRIPT_DIR}/mvnw" >/dev/null 2>&1 || true
fi

echo "✅ Cleanup concluído"
