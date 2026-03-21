#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"

mkdir -p "$LOG_DIR"

pkill -f "spring-boot:run" >/dev/null 2>&1 || true
pkill -f "multitenancy001" >/dev/null 2>&1 || true

find "$ROOT_DIR" -maxdepth 1 -type f \( -name ".newman-report.*.json" -o -name ".env.effective.json" \) -delete || true
rm -f "$LOG_DIR"/app_*.log || true

echo "✅ Cleanup concluído"
