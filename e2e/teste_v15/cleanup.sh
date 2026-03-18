#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"

if [[ -n "${APP_PID:-}" ]]; then
  kill "$APP_PID" >/dev/null 2>&1 || true
fi

rm -f "$SCRIPT_DIR/.env.effective.json" "$SCRIPT_DIR/.newman-report.json" "$SCRIPT_DIR/mvnw"
rm -rf "$SCRIPT_DIR/.mvn"
mkdir -p "$LOG_DIR"
echo "✅ Limpeza concluída"
