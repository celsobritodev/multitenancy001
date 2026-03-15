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
detail() { echo -e "${MAGENTA}     • $*${RESET}"; }
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
APP_LOG="${LOG_DIR}/app_strict_${TIMESTAMP}.log"
REPORT_DIR="${LOG_DIR}/reports_strict_${TIMESTAMP}"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v24.1.transactional-hardening-patched.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v24.1.transactional-hardening-patched.json"
TEMP_ENV="${SCRIPT_DIR}/.env.effective.json"
TEMP_NEWMAN="${SCRIPT_DIR}/.newman-report.strict.json"
APP_PID=""

cleanup() {
  title "Limpando recursos"
  if [[ -n "${APP_PID:-}" ]]; then
    step "Parando aplicação (PID: $APP_PID)"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
  rm -f "${TEMP_NEWMAN}" >/dev/null 2>&1 || true
  [[ -L "${SCRIPT_DIR}/mvnw" ]] && rm -f "${SCRIPT_DIR}/mvnw"
}
trap cleanup EXIT

read_env_value() {
  local key="$1"
  jq -r --arg KEY "$key" '.values[]? | select(.key == $KEY) | .value' "$TEMP_ENV" 2>/dev/null | tail -n 1
}

echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V24.1 TRANSACTIONAL HARDENING PATCHED - STRICT SUITE"
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
ok "Requisitos OK"
hr

title "Verificando porta ${APP_PORT}"
if command -v netstat >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep -q ":${APP_PORT}.*LISTENING"; then
  warn "Porta ${APP_PORT} em uso, liberando..."
  pids=$(netstat -ano | grep ":${APP_PORT}" | grep LISTENING | awk '{print $5}')
  for pid in $pids; do
    taskkill //PID "$pid" //F >/dev/null 2>&1 || true
  done
  sleep 2
fi
ok "Porta ${APP_PORT} OK"
hr

title "Resetando banco"
export PGPASSWORD="${DB_PASSWORD}"
psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
  -c "DROP DATABASE IF EXISTS ${DB_NAME};" >/dev/null 2>&1
psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
  -c "CREATE DATABASE ${DB_NAME};" >/dev/null 2>&1
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

title "Executando suíte strict completa"
newman run "${COLLECTION}" \
  -e "${TEMP_ENV}" \
  --export-environment "${TEMP_ENV}" \
  --reporters cli,json \
  --reporter-json-export "${TEMP_NEWMAN}"
ok "Suíte strict executada"
hr

title "Validando contexto final exportado"
BASE_URL_EFFECTIVE="$(read_env_value base_url)"
[[ -n "${BASE_URL_EFFECTIVE}" && "${BASE_URL_EFFECTIVE}" != "null" ]] || BASE_URL_EFFECTIVE="${BASE_URL}"

TOKEN="$(read_env_value tenant_access_token)"
[[ -n "${TOKEN}" && "${TOKEN}" != "null" ]] || TOKEN="$(read_env_value tenant1_access_token)"

SCHEMA="$(read_env_value tenant_schema)"
[[ -n "${SCHEMA}" && "${SCHEMA}" != "null" ]] || SCHEMA="$(read_env_value tenant1_schema)"

PRODUCT_ID="$(read_env_value product_id)"
[[ -n "${PRODUCT_ID}" && "${PRODUCT_ID}" != "null" ]] || PRODUCT_ID="$(read_env_value product1_id)"

CUSTOMER_ID="$(read_env_value customer2_id)"
[[ -n "${CUSTOMER_ID}" && "${CUSTOMER_ID}" != "null" ]] || CUSTOMER_ID="$(read_env_value customer1_id)"

[[ -n "${TOKEN}" && "${TOKEN}" != "null" ]] || { err "Token tenant ausente após a execução"; exit 1; }
[[ -n "${SCHEMA}" && "${SCHEMA}" != "null" ]] || { err "Schema tenant ausente após a execução"; exit 1; }
[[ -n "${PRODUCT_ID}" && "${PRODUCT_ID}" != "null" ]] || { err "Product ID ausente após a execução"; exit 1; }
[[ -n "${CUSTOMER_ID}" && "${CUSTOMER_ID}" != "null" ]] || { err "Customer ID ausente após a execução"; exit 1; }

step "Base URL efetiva: ${BASE_URL_EFFECTIVE}"
step "Schema efetivo: ${SCHEMA}"
step "Product ID efetivo: ${PRODUCT_ID}"
step "Customer ID efetivo: ${CUSTOMER_ID}"
ok "Contexto final consistente"
hr

title "Smoke final de autenticação/contexto"
curl -fsS \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Tenant: ${SCHEMA}" \
  "${BASE_URL_EFFECTIVE}/api/tenant/me" >/dev/null
ok "Tenant me pós-suite OK"

curl -fsS \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Tenant: ${SCHEMA}" \
  "${BASE_URL_EFFECTIVE}/api/tenant/inventory/products/${PRODUCT_ID}" >/dev/null
ok "Inventory smoke pós-suite OK"
hr

title "Artefatos"
step "App log: ${APP_LOG}"
step "Newman JSON: ${TEMP_NEWMAN}"
step "Report dir: ${REPORT_DIR}"
hr

ok "TESTE V24.1 TRANSACTIONAL HARDENING PATCHED FINALIZADO"
title "V24.0 - Observação"
detail "A V24.0 herda integralmente o modelo operacional da V23.3 corrigida: porta limpa, reset de banco, boot limpo e execução determinística."
detail "Além da base da V23.3, esta linha adiciona probes de hardening transacional para sales/inventory/ledger sem refatorar a suíte do zero."
