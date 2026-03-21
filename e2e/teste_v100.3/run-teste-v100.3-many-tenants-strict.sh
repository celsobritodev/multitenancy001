#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COLLECTION="$ROOT_DIR/multitenancy001.postman_collection.v100.3.many-tenants.json"
ENVIRONMENT="$ROOT_DIR/multitenancy001.local.postman_environment.v100.3.many-tenants.json"
LOG_DIR="$ROOT_DIR/logs"
REPORT_JSON="$ROOT_DIR/.newman-report.strict.json"
EFFECTIVE_ENV="$ROOT_DIR/.env.effective.json"
APP_LOG="$LOG_DIR/app_strict_$(date +%Y%m%d_%H%M%S).log"

mkdir -p "$LOG_DIR"

echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V100.3 MANY TENANTS DATA POPULATION GRID - STRICT SUITE"
echo "   Iniciando execução em: $(date)"
echo "   Path do script: $ROOT_DIR"
echo "────────────────────────────────────────────────────"

echo "🔷 Verificando requisitos"
command -v jq >/dev/null 2>&1 || { echo "❌ jq não encontrado"; exit 1; }
command -v newman >/dev/null 2>&1 || { echo "❌ newman não encontrado"; exit 1; }
echo "✅ Requisitos OK"

echo "────────────────────────────────────────────────────"
echo "🔷 Verificando porta 8080"
if lsof -i :8080 >/dev/null 2>&1; then
  echo "⚠️  Porta 8080 em uso, liberando..."
  lsof -ti :8080 | xargs -r kill -9 || true
  sleep 2
fi
echo "✅ Porta 8080 OK"

echo "────────────────────────────────────────────────────"
echo "🔷 Resetando banco"
if [ -f "$ROOT_DIR/../../mvnw" ]; then
  (cd "$ROOT_DIR/../.." && ./mvnw -q -DskipTests flyway:clean flyway:migrate >/dev/null)
fi
echo "✅ Banco resetado"

echo "────────────────────────────────────────────────────"
echo "🔷 Iniciando aplicação"
(
  cd "$ROOT_DIR/../.."
  ./mvnw spring-boot:run >"$APP_LOG" 2>&1
) &
APP_PID=$!
echo "   → PID: $APP_PID"

echo "==> Aguardando aplicação (timeout: 180s)"
for i in $(seq 1 180); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "✅ Aplicação iniciada"
    break
  fi
  sleep 1
  if [ "$i" -eq 180 ]; then
    echo "❌ Timeout aguardando aplicação"
    kill "$APP_PID" >/dev/null 2>&1 || true
    exit 1
  fi
done

echo "────────────────────────────────────────────────────"
echo "🔷 Health check"
curl -fsS http://localhost:8080/actuator/health >/dev/null
echo "✅ Health check OK"

cp "$ENVIRONMENT" "$EFFECTIVE_ENV"

echo "────────────────────────────────────────────────────"
echo "🔷 Executando suíte de população V100.3"
newman run "$COLLECTION"   -e "$EFFECTIVE_ENV"   --reporters cli,json   --reporter-json-export "$REPORT_JSON"

echo "✅ Suíte V100.3 concluída"
echo "────────────────────────────────────────────────────"
echo "🔷 Relatório final"
echo "   → Collection: $COLLECTION"
echo "   → Environment efetivo: $EFFECTIVE_ENV"
echo "   → App log: $APP_LOG"
echo "   → Newman JSON: $REPORT_JSON"

echo "🔷 Limpando recursos"
echo "   → Parando aplicação (PID: $APP_PID)"
kill "$APP_PID" >/dev/null 2>&1 || true
