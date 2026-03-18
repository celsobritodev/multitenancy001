#!/usr/bin/env bash
set -Eeuo pipefail

clear
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "────────────────────────────────────────"
echo "🔷 CLEANUP V22.1"
echo "   Path do script: $SCRIPT_DIR"
echo "────────────────────────────────────────"

if command -v netstat >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep -q ":8080.*LISTENING"; then
  echo "   → Liberando porta 8080"
  pids=$(netstat -ano | grep ":8080" | grep LISTENING | awk '{print $5}')
  for pid in $pids; do taskkill //PID $pid //F >/dev/null 2>&1 || true; done
fi

rm -f "$SCRIPT_DIR/.env.effective.json" "$SCRIPT_DIR/.newman-report.json" "$SCRIPT_DIR/.chaos-race-report.txt" 2>/dev/null || true
[[ -L "$SCRIPT_DIR/mvnw" ]] && rm -f "$SCRIPT_DIR/mvnw"
[[ -d "$SCRIPT_DIR/.mvn" ]] && rm -rf "$SCRIPT_DIR/.mvn"

echo "✅ Limpeza concluída"
echo "────────────────────────────────────────"
