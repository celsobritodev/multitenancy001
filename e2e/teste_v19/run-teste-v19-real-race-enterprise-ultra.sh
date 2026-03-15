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
LOG_FILE="${LOG_DIR}/execucao_v19_${TIMESTAMP}.log"
APP_LOG="${LOG_DIR}/app_${TIMESTAMP}.log"
REPORT_DIR="${LOG_DIR}/reports_${TIMESTAMP}"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v19.0.real-race-enterprise.strict.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v19.0.real-race-enterprise.strict.json"
TEMP_ENV="${SCRIPT_DIR}/.env.effective.json"
TEMP_NEWMAN="${SCRIPT_DIR}/.newman-report.json"
RACE_REPORT="${SCRIPT_DIR}/.race-report.txt"
APP_PID=""

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"; }

cleanup() {
  local exit_code=$?
  title "Limpando recursos"
  if [[ -n "${APP_PID:-}" ]]; then
    step "Parando aplicação (PID: $APP_PID)"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
  rm -f "${TEMP_NEWMAN}" 2>/dev/null || true
  [[ -L "${SCRIPT_DIR}/mvnw" ]] && rm -f "${SCRIPT_DIR}/mvnw"
  [[ $exit_code -ne 0 ]] && err "Execução falhou"
}
trap cleanup EXIT

read_env_value() {
  local key="$1"
  jq -r --arg KEY "$key" '.values[]? | select(.key == $KEY) | .value' "$TEMP_ENV" 2>/dev/null | tail -n 1
}

update_env_value() {
  local key="$1"
  local value="$2"
  tmp="${TEMP_ENV}.tmp"
  jq --arg KEY "$key" --arg VAL "$value" '
    .values = (
      if ([.values[] | select(.key==$KEY)] | length) > 0 then
        [.values[] | if .key==$KEY then .value=$VAL else . end]
      else
        .values + [{"key":$KEY,"value":$VAL,"enabled":true}]
      end
    )' "$TEMP_ENV" > "$tmp"
  mv "$tmp" "$TEMP_ENV"
}

echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V19.0 - REAL RACE ENTERPRISE (ULTRA SUITE)"
echo "   Iniciando execução em: $(date)"
echo "   Path do script: $SCRIPT_DIR"
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

title "Verificando porta 8080"
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

title "Executando suíte base V19"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --export-environment "${TEMP_ENV}" --reporters cli,json --reporter-json-export "${TEMP_NEWMAN}"
ok "Suíte base executada"
hr

title "Preparando race test real"
BASE_URL_EFFECTIVE="$(read_env_value base_url)"
[[ -n "${BASE_URL_EFFECTIVE}" && "${BASE_URL_EFFECTIVE}" != "null" ]] || BASE_URL_EFFECTIVE="${BASE_URL}"
TOKEN="$(read_env_value tenant_access_token)"
[[ -n "${TOKEN}" && "${TOKEN}" != "null" ]] || TOKEN="$(read_env_value tenant1_access_token)"
SCHEMA="$(read_env_value tenant_schema)"
[[ -n "${SCHEMA}" && "${SCHEMA}" != "null" ]] || SCHEMA="$(read_env_value tenant1_schema)"
CUSTOMER_ID="$(read_env_value customer2_id)"
[[ -n "${CUSTOMER_ID}" && "${CUSTOMER_ID}" != "null" ]] || CUSTOMER_ID="$(read_env_value customer1_id)"
PRODUCT_ID="$(read_env_value product_id)"
UNIT_PRICE="$(read_env_value race_unit_price)"
PRODUCT_NAME="$(read_env_value race_product_name)"
PARALLELISM="$(read_env_value race_parallelism)"
ATTEMPTS="$(read_env_value race_attempt_count)"
[[ -n "${PARALLELISM}" && "${PARALLELISM}" != "null" ]] || PARALLELISM="20"
[[ -n "${ATTEMPTS}" && "${ATTEMPTS}" != "null" ]] || ATTEMPTS="20"
[[ -n "${UNIT_PRICE}" && "${UNIT_PRICE}" != "null" ]] || UNIT_PRICE="1999.99"
[[ -n "${PRODUCT_NAME}" && "${PRODUCT_NAME}" != "null" ]] || PRODUCT_NAME="Smartphone XYZ"

[[ -n "${TOKEN}" && "${TOKEN}" != "null" ]] || { err "tenant token ausente"; exit 1; }
[[ -n "${SCHEMA}" && "${SCHEMA}" != "null" ]] || { err "tenant schema ausente"; exit 1; }
[[ -n "${CUSTOMER_ID}" && "${CUSTOMER_ID}" != "null" ]] || { err "customer id ausente"; exit 1; }
[[ -n "${PRODUCT_ID}" && "${PRODUCT_ID}" != "null" ]] || { err "product id ausente"; exit 1; }

step "Base URL: ${BASE_URL_EFFECTIVE}"
step "Schema: ${SCHEMA}"
step "Customer ID: ${CUSTOMER_ID}"
step "Product ID: ${PRODUCT_ID}"
step "Attempts: ${ATTEMPTS}"
step "Parallelism: ${PARALLELISM}"

: > "${RACE_REPORT}"

title "Disparando ${ATTEMPTS} vendas simultâneas reais"
success_count=0
failure_count=0

pids=()
statuses_file="${REPORT_DIR}/race-statuses.txt"
: > "${statuses_file}"

for i in $(seq 1 "${ATTEMPTS}"); do
  (
    code="$("${SCRIPT_DIR}/race-worker-sale.sh" "${BASE_URL_EFFECTIVE}" "${TOKEN}" "${SCHEMA}" "${CUSTOMER_ID}" "${PRODUCT_ID}" "${UNIT_PRICE}" "${PRODUCT_NAME}")"
    echo "${code}" >> "${statuses_file}"
  ) &
  pids+=($!)
  # limitar concorrência
  while [[ "$(jobs -rp | wc -l)" -ge "${PARALLELISM}" ]]; do
    sleep 0.05
  done
done

for pid in "${pids[@]}"; do
  wait "${pid}" || true
done

while read -r code; do
  if [[ "${code}" == "200" || "${code}" == "201" ]]; then
    success_count=$((success_count+1))
  else
    failure_count=$((failure_count+1))
  fi
done < "${statuses_file}"

echo "attempts=${ATTEMPTS}" > "${RACE_REPORT}"
echo "parallelism=${PARALLELISM}" >> "${RACE_REPORT}"
echo "success_count=${success_count}" >> "${RACE_REPORT}"
echo "failure_count=${failure_count}" >> "${RACE_REPORT}"

update_env_value "race_success_count" "${success_count}"
update_env_value "race_failure_count" "${failure_count}"

ok "Race concluído: ${success_count} sucesso(s), ${failure_count} falha(s)"
hr

title "Verificando inventory e movements após race real"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --folder "📦 92 - REAL RACE VERIFY" --reporters cli,json --reporter-json-export "${REPORT_DIR}/real-race-verify.json"
ok "Verificação pós-race concluída"
hr

title "Resumo do race test"
cat "${RACE_REPORT}"
hr

ok "TESTE V19.0 ULTRA FINALIZADO"
