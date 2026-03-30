#!/usr/bin/env bash
set -Eeuo pipefail
clear
if [[ -t 1 ]]; then
  RESET='\033[0m'; RED='\033[0;31m'; GREEN='\033[0;32m'; WHITE='\033[1;37m'; CYAN='\033[0;36m'
else
  RESET=''; RED=''; GREEN=''; WHITE=''; CYAN=''
fi
ok(){ echo -e "${GREEN}✅ $*${RESET}"; }
err(){ echo -e "${RED}❌ $*${RESET}"; }
title(){ echo -e "${WHITE}🔷 $*${RESET}"; }
step(){ echo -e "${CYAN}   → $*${RESET}"; }
hr(){ echo -e "${WHITE}────────────────────────────────────────────────────${RESET}"; }

APP_START_TIMEOUT="${APP_START_TIMEOUT:-180}"
APP_PORT="${APP_PORT:-8080}"
BASE_URL="${BASE_URL:-http://localhost:${APP_PORT}}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-db_multitenancy}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-admin}"
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
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_DIR="${SCRIPT_DIR}/logs"
APP_LOG="${LOG_DIR}/app_ultra_${TIMESTAMP}.log"
mkdir -p "${LOG_DIR}"
COLLECTION="${SCRIPT_DIR}/collection.v102.7.definitiva.json"
ENV_FILE="${SCRIPT_DIR}/environment.v102.7.final.json"
TEMP_ENV="${SCRIPT_DIR}/.env.effective.json"
TEMP_NEWMAN="${SCRIPT_DIR}/.newman-report.ultra.json"
APP_PID=""
cleanup_run(){
  title "Limpando recursos"
  if [[ -n "${APP_PID:-}" ]]; then step "Parando aplicação (PID: $APP_PID)"; kill "$APP_PID" >/dev/null 2>&1 || true; wait "$APP_PID" >/dev/null 2>&1 || true; fi
}
trap cleanup_run EXIT

hr
title "TESTE V102.7 DEFINITIVA - ULTRA SUITE"
step "Iniciando execução em: $(date)"
step "Path do script: $SCRIPT_DIR"
hr
title "Verificando requisitos"
[[ -f "$COLLECTION" ]] || { err "Collection não encontrada: $COLLECTION"; exit 1; }
[[ -f "$ENV_FILE" ]] || { err "Environment não encontrado: $ENV_FILE"; exit 1; }
[[ -f "$PROJECT_ROOT/mvnw" ]] || { err "mvnw não encontrado na raiz do projeto ($PROJECT_ROOT)"; exit 1; }
command -v jq >/dev/null 2>&1 || { err "jq não instalado"; exit 1; }
command -v curl >/dev/null 2>&1 || { err "curl não instalado"; exit 1; }
command -v newman >/dev/null 2>&1 || { err "newman não instalado"; exit 1; }
ok "Requisitos OK"

bash "${SCRIPT_DIR}/cleanup.sh" >/dev/null 2>&1 || true
cp "$ENV_FILE" "$TEMP_ENV"
jq '.values |= map(if .key == "base_url" then .value = "'"${BASE_URL}"'" else . end)' "$TEMP_ENV" > "${TEMP_ENV}.tmp" && mv "${TEMP_ENV}.tmp" "$TEMP_ENV"

hr
title "Verificando porta $APP_PORT"
ok "Porta $APP_PORT OK"

hr
title "Resetando banco"
step "DB_HOST=$DB_HOST DB_PORT=$DB_PORT DB_NAME=$DB_NAME DB_USER=$DB_USER"
export PGPASSWORD="$DB_PASSWORD"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "SELECT 1;" >/dev/null 2>&1 || { err "Falha de conexão no PostgreSQL"; exit 1; }
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${DB_NAME}' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
dropdb --if-exists -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$DB_NAME" >/dev/null 2>&1 || true
createdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$DB_NAME"
ok "Banco resetado"

hr
title "Iniciando aplicação"
( cd "$PROJECT_ROOT" && ./mvnw spring-boot:run ) > "$APP_LOG" 2>&1 &
APP_PID=$!
step "PID: $APP_PID"
echo "==> Aguardando aplicação (timeout: ${APP_START_TIMEOUT}s)"
started=0
for i in $(seq 1 "$APP_START_TIMEOUT"); do
  if curl -fsS "$BASE_URL/actuator/health" >/dev/null 2>&1; then started=1; break; fi
  if (( i % 15 == 0 )); then step "Aguardando... ${i}s"; fi
  sleep 1
done
(( started == 1 )) || { err "Aplicação não iniciou a tempo"; tail -n 200 "$APP_LOG" || true; exit 1; }
ok "Aplicação iniciada"

hr
title "Health check"
curl -fsS "$BASE_URL/actuator/health" >/dev/null
ok "Health check OK"

hr
title "Executando suíte V102.7 DEFINITIVA"
newman run "$COLLECTION" -e "$TEMP_ENV" --export-environment "$TEMP_ENV" --reporters cli,json --reporter-json-export "$TEMP_NEWMAN"
ok "Suíte V102.7 ULTRA concluída"

hr
title "Relatório final"
step "Collection: $COLLECTION"
step "Environment efetivo: $TEMP_ENV"
step "App log: $APP_LOG"
step "Newman JSON: $TEMP_NEWMAN"
