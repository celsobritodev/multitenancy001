#!/usr/bin/env bash
set -Eeuo pipefail
clear
if [[ -t 1 ]]; then
  RESET='\033[0m'; RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
  BLUE='\033[0;34m'; WHITE='\033[1;37m'; CYAN='\033[0;36m'; MAGENTA='\033[0;35m'
else
  RESET=''; RED=''; GREEN=''; YELLOW=''; BLUE=''; WHITE=''; CYAN=''; MAGENTA=''
fi
ok() { echo -e "${GREEN}✅ $*${RESET}"; }
warn() { echo -e "${YELLOW}⚠️  $*${RESET}"; }
err() { echo -e "${RED}❌ $*${RESET}"; }
info() { echo -e "${BLUE}==> $*${RESET}"; }
title() { echo -e "${WHITE}🔷 $*${RESET}"; }
step() { echo -e "${CYAN}   → $*${RESET}"; }
hr() { echo -e "${WHITE}────────────────────────────────────────────────────${RESET}"; }
APP_START_TIMEOUT="${APP_START_TIMEOUT:-180}"
APP_PORT="${APP_PORT:-8080}"
BASE_URL="${BASE_URL:-http://localhost:${APP_PORT}}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-db_multitenancy}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-admin}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_DIR="${SCRIPT_DIR}/logs"
APP_LOG="${LOG_DIR}/app_v100_ultra_${TIMESTAMP}.log"
REPORT_JSON="${SCRIPT_DIR}/.newman-report.v100.ultra.json"
COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v100.heavy-data-population-grid.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v100.heavy-data-population-grid.json"
TEMP_ENV="${SCRIPT_DIR}/.env.effective.v100.json"
APP_PID=""
mkdir -p "${LOG_DIR}"
cleanup() {
  title "Limpando recursos"
  if [[ -n "${APP_PID:-}" ]]; then
    step "Parando aplicação (PID: $APP_PID)"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
  [[ -L "${SCRIPT_DIR}/mvnw" ]] && rm -f "${SCRIPT_DIR}/mvnw"
}
trap cleanup EXIT
echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V100 HEAVY DATA POPULATION GRID - ULTRA SUITE"
echo "   Iniciando execução em: $(date)"
echo "   Path do script: ${SCRIPT_DIR}"
echo "────────────────────────────────────────────────────"
title "Verificando requisitos"
[[ -f "${COLLECTION}" ]] || { err "Collection não encontrada"; exit 1; }
[[ -f "${ENV_FILE}" ]] || { err "Environment não encontrado"; exit 1; }
[[ -f "${PROJECT_ROOT}/mvnw" ]] || { err "mvnw não encontrado na raiz"; exit 1; }
command -v jq >/dev/null 2>&1 || { err "jq não instalado"; exit 1; }
command -v curl >/dev/null 2>&1 || { err "curl não instalado"; exit 1; }
command -v newman >/dev/null 2>&1 || { err "newman não instalado"; exit 1; }
command -v psql >/dev/null 2>&1 || { err "psql não instalado"; exit 1; }
ok "Requisitos OK"
hr
title "Verificando porta ${APP_PORT}"
if command -v netstat >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep -q ":${APP_PORT}.*LISTENING"; then
  warn "Porta ${APP_PORT} em uso, liberando..."
  pids=$(netstat -ano | grep ":${APP_PORT}" | grep LISTENING | awk '{print $5}')
  for pid in $pids; do taskkill //PID "$pid" //F >/dev/null 2>&1 || true; done
  sleep 2
fi
ok "Porta ${APP_PORT} OK"
hr
title "Resetando banco"
export PGPASSWORD="${DB_PASSWORD}"
psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "DROP DATABASE IF EXISTS ${DB_NAME};" >/dev/null 2>&1
psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "CREATE DATABASE ${DB_NAME};" >/dev/null 2>&1
ok "Banco resetado"
hr
title "Criando link para mvnw"
ln -sf "${PROJECT_ROOT}/mvnw" "${SCRIPT_DIR}/mvnw" 2>/dev/null || true
ok "Link criado"
hr
title "Preparando environment"
cp "${ENV_FILE}" "${TEMP_ENV}"
ok "Environment pronto"
hr
title "Iniciando aplicação"
: > "${APP_LOG}"
(cd "${PROJECT_ROOT}" && ./mvnw spring-boot:run) > "${APP_LOG}" 2>&1 &
APP_PID=$!
step "PID: $APP_PID"
info "Aguardando aplicação (timeout: ${APP_START_TIMEOUT}s)"
for i in $(seq 1 "${APP_START_TIMEOUT}"); do
  if grep -Eq "Started .*Application|Started .* in .* seconds" "${APP_LOG}" 2>/dev/null; then
    ok "Aplicação iniciada em ${i}s"
    break
  fi
  sleep 1
  [[ $i -eq ${APP_START_TIMEOUT} ]] && { err "Timeout aguardando aplicação"; tail -50 "${APP_LOG}" || true; exit 1; }
done
title "Health check"
curl -fsS "${BASE_URL}/actuator/health" >/dev/null
ok "Health check OK"
hr
title "Executando suíte de população V100"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --export-environment "${TEMP_ENV}" --reporters cli,json --reporter-json-export "${REPORT_JSON}"
ok "Suíte V100 executada"
hr
title "Artefatos"
step "App log: ${APP_LOG}"
step "Newman JSON: ${REPORT_JSON}"
hr
ok "TESTE V100 HEAVY DATA POPULATION GRID FINALIZADO"
