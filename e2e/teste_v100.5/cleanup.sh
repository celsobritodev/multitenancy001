#!/usr/bin/env bash
set -euo pipefail
rm -f .env.effective.json .newman-report.strict.json .newman-report.ultra.json || true
mkdir -p logs
echo "✅ Cleanup concluído"
