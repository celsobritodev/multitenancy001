#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
find "$SCRIPT_DIR/logs" -type f ! -name '.gitkeep' -delete 2>/dev/null || true
rm -f "$SCRIPT_DIR/.env.effective.json" "$SCRIPT_DIR/.newman-report.json" "$SCRIPT_DIR/.newman-report.strict.json" 2>/dev/null || true
echo "✅ Limpeza concluída em $SCRIPT_DIR"
