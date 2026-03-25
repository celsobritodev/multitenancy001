#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COLLECTION="$SCRIPT_DIR/multitenancy001.postman_collection.v100.5.enterprise-many-tenants-no400.json"
ENV_FILE="$SCRIPT_DIR/multitenancy001.local.postman_environment.v100.5.enterprise-many-tenants-no400.json"
EFFECTIVE_ENV="$SCRIPT_DIR/.env.effective.json"
MODE="${1:-strict}"
if [[ "$MODE" == "strict" ]]; then REPORT_JSON="$SCRIPT_DIR/.newman-report.strict.json"; APP_LOG="$SCRIPT_DIR/logs/app_strict_$(date +%Y%m%d_%H%M%S).log"; TITLE="STRICT"; else REPORT_JSON="$SCRIPT_DIR/.newman-report.ultra.json"; APP_LOG="$SCRIPT_DIR/logs/app_ultra_$(date +%Y%m%d_%H%M%S).log"; TITLE="ULTRA"; fi
echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V100.5 ENTERPRISE MANY TENANTS NO-400 - ${TITLE} SUITE"
echo "   Iniciando execução em: $(date)"
echo "   Path do script: $SCRIPT_DIR"
echo "────────────────────────────────────────────────────"
echo "🔷 Verificando requisitos"
command -v node >/dev/null 2>&1 || { echo "❌ Node.js não encontrado"; exit 1; }
command -v newman >/dev/null 2>&1 || { echo "❌ Newman não encontrado"; exit 1; }
echo "✅ Requisitos OK"
echo "────────────────────────────────────────────────────"
echo "🔷 Verificando porta 8080"
if command -v lsof >/dev/null 2>&1; then PIDS="$(lsof -ti :8080 || true)"; if [[ -n "${PIDS}" ]]; then echo "⚠️  Porta 8080 em uso, liberando..."; kill -9 ${PIDS} || true; sleep 2; fi; fi
echo "✅ Porta 8080 OK"
echo "────────────────────────────────────────────────────"
echo "🔷 Resetando banco"
if [[ -x "$PROJECT_ROOT/reset-db.sh" ]]; then (cd "$PROJECT_ROOT" && ./reset-db.sh); else echo "⚠️ reset-db.sh não encontrado. Ajuste conforme seu projeto."; fi
echo "✅ Banco resetado"
echo "────────────────────────────────────────────────────"
echo "🔷 Iniciando aplicação"
mkdir -p "$SCRIPT_DIR/logs"
(cd "$PROJECT_ROOT" && ./mvnw spring-boot:run > "$APP_LOG" 2>&1) &
APP_PID=$!
echo "   → PID: $APP_PID"
trap 'echo "🔷 Limpando recursos"; echo "   → Parando aplicação (PID: '"$APP_PID"')"; kill "$APP_PID" >/dev/null 2>&1 || true' EXIT
echo "==> Aguardando aplicação (timeout: 180s)"
for i in $(seq 1 180); do if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then echo "✅ Aplicação iniciada"; break; fi; sleep 1; if [[ "$i" -eq 180 ]]; then echo "❌ Timeout aguardando aplicação"; exit 1; fi; done
echo "────────────────────────────────────────────────────"
echo "🔷 Health check"
curl -fsS http://localhost:8080/actuator/health >/dev/null
echo "✅ Health check OK"
cp "$ENV_FILE" "$EFFECTIVE_ENV"
echo "────────────────────────────────────────────────────"
echo "🔷 Executando suíte de população V100.5"
newman run "$COLLECTION" -e "$EFFECTIVE_ENV" --reporters cli,json --reporter-json-export "$REPORT_JSON"
echo "✅ Suíte V100.5 concluída"
echo "────────────────────────────────────────────────────"
echo "🔷 Relatório final"
echo "   → Collection: $COLLECTION"
echo "   → Environment efetivo: $EFFECTIVE_ENV"
echo "   → App log: $APP_LOG"
echo "   → Newman JSON: $REPORT_JSON"
