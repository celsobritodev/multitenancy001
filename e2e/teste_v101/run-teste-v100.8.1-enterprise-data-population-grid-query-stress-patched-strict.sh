#!/usr/bin/env bash
set -Eeuo pipefail
clear
if [[ -t 1 ]]; then
  RESET='[0m'; RED='[0;31m'; GREEN='[0;32m'; YELLOW='[1;33m'; BLUE='[0;34m'; WHITE='[1;37m'; CYAN='[0;36m'; MAGENTA='[0;35m'
else
  RESET=''; RED=''; GREEN=''; YELLOW=''; BLUE=''; WHITE=''; CYAN=''; MAGENTA=''
fi
ok(){ echo -e "${GREEN}✅ $*${RESET}"; }
warn(){ echo -e "${YELLOW}⚠️  $*${RESET}"; }
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
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_DIR="${SCRIPT_DIR}/logs"
APP_LOG="${LOG_DIR}/app_strict_${TIMESTAMP}.log"
REPORT_DIR="${LOG_DIR}/reports_strict_${TIMESTAMP}"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"
COLLECTION="${SCRIPT_DIR}/collection.v100.8.1.json"
ENV_FILE="${SCRIPT_DIR}/environment.v100.8.1.json"
TEMP_ENV="${SCRIPT_DIR}/.env.effective.json"
TEMP_NEWMAN="${SCRIPT_DIR}/.newman-report.strict.json"
APP_PID=""
cleanup(){
  title "Limpando recursos"
  if [[ -n "${APP_PID:-}" ]]; then step "Parando aplicação (PID: $APP_PID)"; kill "$APP_PID" >/dev/null 2>&1 || true; wait "$APP_PID" >/dev/null 2>&1 || true; fi
  rm -f "${TEMP_NEWMAN}" >/dev/null 2>&1 || true
  [[ -L "${SCRIPT_DIR}/mvnw" ]] && rm -f "${SCRIPT_DIR}/mvnw"
}
trap cleanup EXIT
echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V100.8.1 HEAVY DATA POPULATION GRID QUERY STRESS PATCHED - STRICT SUITE"
echo "   Iniciando execução em: $(date)"
echo "   Path do script: ${SCRIPT_DIR}"
echo "────────────────────────────────────────────────────"
title "Verificando requisitos"
[[ -f "${COLLECTION}" ]] || COLLECTION="${}"
[[ -f "${ENV_FILE}" ]] || ENV_FILE="${}"
[[ -f "${COLLECTION}" ]] || COLLECTION="${}"
[[ -f "${COLLECTION}" ]] || COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v100.heavy-data-population-grid.json"
[[ -f "${COLLECTION}" ]] || { err "Collection não encontrada"; exit 1; }
[[ -f "${ENV_FILE}" ]] || ENV_FILE="${}"
[[ -f "${ENV_FILE}" ]] || ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v100.heavy-data-population-grid.json"
[[ -f "${ENV_FILE}" ]] || { err "Environment não encontrado"; exit 1; }
[[ -f "${PROJECT_ROOT}/mvnw" ]] || { err "mvnw não encontrado na raiz do projeto (${PROJECT_ROOT})"; exit 1; }
command -v jq >/dev/null 2>&1 || { err "jq não instalado"; exit 1; }
command -v curl >/dev/null 2>&1 || { err "curl não instalado"; exit 1; }
command -v newman >/dev/null 2>&1 || { err "newman não instalado"; exit 1; }
ok "Requisitos OK"; hr
bash "${SCRIPT_DIR}/cleanup.sh" >/dev/null 2>&1 || true
cp "${ENV_FILE}" "${TEMP_ENV}"
ln -sf "${PROJECT_ROOT}/mvnw" "${SCRIPT_DIR}/mvnw"
[[ -d "${PROJECT_ROOT}/.mvn" && ! -e "${SCRIPT_DIR}/.mvn" ]] && cp -R "${PROJECT_ROOT}/.mvn" "${SCRIPT_DIR}/.mvn"
title "Verificando porta ${APP_PORT}"
if command -v netstat >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep -q ":${APP_PORT}.*LISTENING"; then warn "Porta ${APP_PORT} em uso, liberando..."; pids=$(netstat -ano | grep ":${APP_PORT}" | grep LISTENING | awk '{print $5}'); for pid in $pids; do taskkill //PID "$pid" //F >/dev/null 2>&1 || true; done; sleep 2; fi
ok "Porta ${APP_PORT} OK"; hr
title "Resetando banco"
PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${DB_NAME}' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
PGPASSWORD="${DB_PASSWORD}" dropdb --if-exists -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" "${DB_NAME}" >/dev/null 2>&1 || true
PGPASSWORD="${DB_PASSWORD}" createdb -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" "${DB_NAME}"
ok "Banco resetado"; hr
title "Iniciando aplicação"
( cd "${PROJECT_ROOT}" && ./mvnw spring-boot:run ) > "${APP_LOG}" 2>&1 &
APP_PID=$!
step "PID: ${APP_PID}"
echo "==> Aguardando aplicação (timeout: ${APP_START_TIMEOUT}s)"
started=0
for i in $(seq 1 "${APP_START_TIMEOUT}"); do
  if grep -q "Started .* in .* seconds" "${APP_LOG}" 2>/dev/null; then started=1; break; fi
  sleep 1
done
(( started == 1 )) || { err "Aplicação não iniciou a tempo"; tail -n 200 "${APP_LOG}" || true; exit 1; }
ok "Aplicação iniciada"; hr
title "Health check"
curl -fsS "${BASE_URL}/actuator/health" >/dev/null
ok "Health check OK"; hr
title "Executando suíte de população V100"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --export-environment "${TEMP_ENV}" --reporters cli,json --reporter-json-export "${TEMP_NEWMAN}"
ok "Suíte V100 concluída"; hr
title "Relatório final"
step "Collection: ${COLLECTION}"
step "Environment efetivo: ${TEMP_ENV}"
step "App log: ${APP_LOG}"
step "Newman JSON: ${TEMP_NEWMAN}"