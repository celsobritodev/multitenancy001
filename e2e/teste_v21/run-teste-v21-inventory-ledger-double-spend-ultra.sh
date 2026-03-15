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
LOG_FILE="${LOG_DIR}/execucao_v21_${TIMESTAMP}.log"
APP_LOG="${LOG_DIR}/app_${TIMESTAMP}.log"
REPORT_DIR="${LOG_DIR}/reports_${TIMESTAMP}"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v21.0.inventory-ledger-double-spend-reconciliation.strict.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v21.0.inventory-ledger-double-spend-reconciliation.strict.json"
TEMP_ENV="${SCRIPT_DIR}/.env.effective.json"
TEMP_NEWMAN="${SCRIPT_DIR}/.newman-report.json"
CHAOS_RACE_REPORT="${SCRIPT_DIR}/.chaos-race-report.txt"
WORKER_SCRIPT="${SCRIPT_DIR}/chaos-race-worker-sale.sh"
APP_PID=""

declare -A HTTP_COUNTS=()
declare -A API_COUNTS=()
declare -A HTTP_API_COUNTS=()

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

update_env_value() {
  local key="$1"
  local value="$2"
  local tmp="${TEMP_ENV}.tmp"
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

inventory_affecting_status() {
  case "$1" in
    OPEN|CONFIRMED|PAID) echo 1 ;;
    *) echo 0 ;;
  esac
}

inc_map() {
  local name="$1"
  local key="$2"
  eval "$name[\"$key\"]=$(( \\${$name[\"$key\"]:-0} + 1 ))"
}

write_sorted_map() {
  local name="$1"
  local out_file="$2"
  : > "$out_file"
  eval 'for k in "${!'"$name"'[@]}"; do printf "%s\t%s\n" "$k" "${'"$name"'[$k]}"; done' | sort >> "$out_file"
}

echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V21.0 - INVENTORY LEDGER / DOUBLE-SPEND RECONCILIATION (ULTRA SUITE CORRIGIDA)"
echo "   Iniciando execução em: $(date)"
echo "   Path do script: $SCRIPT_DIR"
echo "────────────────────────────────────────────────────"

title "Verificando requisitos"
[[ -f "${COLLECTION}" ]] || { err "Collection não encontrada"; exit 1; }
[[ -f "${ENV_FILE}" ]] || { err "Environment não encontrado"; exit 1; }
[[ -f "${PROJECT_ROOT}/mvnw" ]] || { err "mvnw não encontrado na raiz"; exit 1; }
[[ -x "${WORKER_SCRIPT}" ]] || { err "Worker script não encontrado/executável: ${WORKER_SCRIPT}"; exit 1; }
command -v jq >/dev/null 2>&1 || { err "jq não instalado"; exit 1; }
command -v curl >/dev/null 2>&1 || { err "curl não instalado"; exit 1; }
command -v newman >/dev/null 2>&1 || { err "newman não instalado"; exit 1; }
command -v python >/dev/null 2>&1 || command -v python3 >/dev/null 2>&1 || { err "python não instalado"; exit 1; }
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

title "Executando suíte base V21"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --export-environment "${TEMP_ENV}" --reporters cli,json --reporter-json-export "${TEMP_NEWMAN}"
ok "Suíte base executada"
hr

title "Preparando chaos race test real"
BASE_URL_EFFECTIVE="$(read_env_value base_url)"
[[ -n "${BASE_URL_EFFECTIVE}" && "${BASE_URL_EFFECTIVE}" != "null" ]] || BASE_URL_EFFECTIVE="${BASE_URL}"
TOKEN="$(read_env_value tenant_access_token)"
[[ -n "${TOKEN}" && "${TOKEN}" != "null" ]] || TOKEN="$(read_env_value tenant1_access_token)"
SCHEMA="$(read_env_value tenant_schema)"
[[ -n "${SCHEMA}" && "${SCHEMA}" != "null" ]] || SCHEMA="$(read_env_value tenant1_schema)"
CUSTOMER_ID="$(read_env_value customer2_id)"
[[ -n "${CUSTOMER_ID}" && "${CUSTOMER_ID}" != "null" ]] || CUSTOMER_ID="$(read_env_value customer1_id)"
PRODUCT_ID="$(read_env_value product_id)"
UNIT_PRICE="$(read_env_value chaos_race_unit_price)"
PRODUCT_NAME="$(read_env_value chaos_race_product_name)"
PARALLELISM="$(read_env_value chaos_race_parallelism)"
ATTEMPTS="$(read_env_value chaos_race_attempt_count)"
MAX_JITTER_MS="$(read_env_value chaos_race_max_jitter_ms)"
BASE_DELAY_MS="$(read_env_value chaos_race_base_delay_ms)"
RETRY_COUNT="$(read_env_value chaos_race_retry_count)"
SALE_STATUS="$(read_env_value chaos_race_sale_status)"

[[ -n "${PARALLELISM}" && "${PARALLELISM}" != "null" ]] || PARALLELISM="100"
[[ -n "${ATTEMPTS}" && "${ATTEMPTS}" != "null" ]] || ATTEMPTS="100"
[[ -n "${MAX_JITTER_MS}" && "${MAX_JITTER_MS}" != "null" ]] || MAX_JITTER_MS="250"
[[ -n "${BASE_DELAY_MS}" && "${BASE_DELAY_MS}" != "null" ]] || BASE_DELAY_MS="20"
[[ -n "${RETRY_COUNT}" && "${RETRY_COUNT}" != "null" ]] || RETRY_COUNT="2"
[[ -n "${UNIT_PRICE}" && "${UNIT_PRICE}" != "null" ]] || UNIT_PRICE="1999.99"
[[ -n "${PRODUCT_NAME}" && "${PRODUCT_NAME}" != "null" ]] || PRODUCT_NAME="Smartphone XYZ"
[[ -n "${SALE_STATUS}" && "${SALE_STATUS}" != "null" ]] || SALE_STATUS="OPEN"

[[ -n "${TOKEN}" && "${TOKEN}" != "null" ]] || { err "tenant token ausente"; exit 1; }
[[ -n "${SCHEMA}" && "${SCHEMA}" != "null" ]] || { err "tenant schema ausente"; exit 1; }
[[ -n "${CUSTOMER_ID}" && "${CUSTOMER_ID}" != "null" ]] || { err "customer id ausente"; exit 1; }
[[ -n "${PRODUCT_ID}" && "${PRODUCT_ID}" != "null" ]] || { err "product id ausente"; exit 1; }

SALE_AFFECTS_INVENTORY="$(inventory_affecting_status "$SALE_STATUS")"
update_env_value "chaos_race_sale_status" "$SALE_STATUS"
update_env_value "chaos_race_sale_status_affects_inventory" "$SALE_AFFECTS_INVENTORY"

step "Base URL: ${BASE_URL_EFFECTIVE}"
step "Schema: ${SCHEMA}"
step "Customer ID: ${CUSTOMER_ID}"
step "Product ID: ${PRODUCT_ID}"
step "Sale status: ${SALE_STATUS}"
step "Affects inventory: ${SALE_AFFECTS_INVENTORY}"
step "Attempts: ${ATTEMPTS}"
step "Parallelism: ${PARALLELISM}"
step "Max jitter (ms): ${MAX_JITTER_MS}"
step "Base delay (ms): ${BASE_DELAY_MS}"
step "Retry count: ${RETRY_COUNT}"

: > "${CHAOS_RACE_REPORT}"
statuses_file="${REPORT_DIR}/chaos-race-statuses.tsv"
worker_log_dir="${REPORT_DIR}/workers"
http_counts_file="${REPORT_DIR}/chaos-http-status-counts.txt"
api_counts_file="${REPORT_DIR}/chaos-api-code-counts.txt"
http_api_counts_file="${REPORT_DIR}/chaos-http-api-code-counts.txt"
mkdir -p "${worker_log_dir}"
: > "${statuses_file}"

title "Disparando ${ATTEMPTS} vendas simultâneas reais com chaos"
pids=()
for i in $(seq 1 "${ATTEMPTS}"); do
  (
    result="$(${WORKER_SCRIPT} "${BASE_URL_EFFECTIVE}" "${TOKEN}" "${SCHEMA}" "${CUSTOMER_ID}" "${PRODUCT_ID}" "${UNIT_PRICE}" "${PRODUCT_NAME}" "${MAX_JITTER_MS}" "${BASE_DELAY_MS}" "${RETRY_COUNT}" "${SALE_STATUS}" "$i" "${worker_log_dir}")"
    echo "${result}" >> "${statuses_file}"
  ) &
  pids+=($!)
  while [[ "$(jobs -rp | wc -l | tr -d ' ')" -ge "${PARALLELISM}" ]]; do
    sleep 0.03
  done
done
for pid in "${pids[@]}"; do
  wait "${pid}" || true
done

success_count=0
failure_count=0
accepted_affecting_count=0
accepted_non_affecting_count=0
rollback_like_count=0
count_200_201=0
count_409_total=0
count_409_insufficient=0
count_400=0
count_401=0
count_403=0
count_404=0
count_500=0
sample_sale_ids=()

while IFS=$'\t' read -r code status affects sale_id; do
  [[ -n "${code:-}" ]] || continue
  if [[ "$code" == "200" || "$code" == "201" ]]; then
    success_count=$((success_count+1))
    count_200_201=$((count_200_201+1))
    if [[ "$affects" == "1" ]]; then
      accepted_affecting_count=$((accepted_affecting_count+1))
    else
      accepted_non_affecting_count=$((accepted_non_affecting_count+1))
    fi
    if [[ -n "${sale_id:-}" && ${#sample_sale_ids[@]} -lt 10 ]]; then
      sample_sale_ids+=("$sale_id")
    fi
  else
    failure_count=$((failure_count+1))
  fi
  case "$code" in
    400) count_400=$((count_400+1)) ;;
    401) count_401=$((count_401+1)) ;;
    403) count_403=$((count_403+1)) ;;
    404) count_404=$((count_404+1)) ;;
    409) count_409_total=$((count_409_total+1)) ;;
    500) count_500=$((count_500+1)) ;;
  esac
  if [[ "$code" == "400" || "$code" == "404" || "$code" == "409" || "$code" == "422" ]]; then
    rollback_like_count=$((rollback_like_count+1))
  fi
done < "${statuses_file}"

if [[ -d "${worker_log_dir}" ]]; then
  count_409_insufficient="$(find "${worker_log_dir}" -type f -name 'worker_*.log' -print0 2>/dev/null | xargs -0 -r -I{} bash -lc 'last=$(tail -n 1 "$1" 2>/dev/null || true); [[ "$last" == *"code=409"* && "$last" == *"INSUFFICIENT_STOCK"* ]] && echo 1 || echo 0' _ {} | awk '{s+=$1} END {print s+0}' 2>/dev/null || echo 0)"
fi

qty_per_sale=1
inventory_before_real_race="$(read_env_value inventory_before_chaos_race)"
movements_before_real_race="$(read_env_value movements_before_chaos_race)"
[[ -n "${inventory_before_real_race}" && "${inventory_before_real_race}" != "null" ]] || inventory_before_real_race=0
[[ -n "${movements_before_real_race}" && "${movements_before_real_race}" != "null" ]] || movements_before_real_race=0

expected_consumption=$(( accepted_affecting_count * qty_per_sale ))
expected_inventory_after=$(( inventory_before_real_race - expected_consumption ))

printf 'attempts=%s\n' "${ATTEMPTS}" > "${CHAOS_RACE_REPORT}"
printf 'parallelism=%s\n' "${PARALLELISM}" >> "${CHAOS_RACE_REPORT}"
printf 'sale_status=%s\n' "${SALE_STATUS}" >> "${CHAOS_RACE_REPORT}"
printf 'sale_status_affects_inventory=%s\n' "${SALE_AFFECTS_INVENTORY}" >> "${CHAOS_RACE_REPORT}"
printf 'max_jitter_ms=%s\n' "${MAX_JITTER_MS}" >> "${CHAOS_RACE_REPORT}"
printf 'base_delay_ms=%s\n' "${BASE_DELAY_MS}" >> "${CHAOS_RACE_REPORT}"
printf 'retry_count=%s\n' "${RETRY_COUNT}" >> "${CHAOS_RACE_REPORT}"
printf 'success_count=%s\n' "${success_count}" >> "${CHAOS_RACE_REPORT}"
printf 'failure_count=%s\n' "${failure_count}" >> "${CHAOS_RACE_REPORT}"
printf 'accepted_affecting_count=%s\n' "${accepted_affecting_count}" >> "${CHAOS_RACE_REPORT}"
printf 'accepted_non_affecting_count=%s\n' "${accepted_non_affecting_count}" >> "${CHAOS_RACE_REPORT}"
printf 'rollback_like_count=%s\n' "${rollback_like_count}" >> "${CHAOS_RACE_REPORT}"
printf 'inventory_before_real_race=%s\n' "${inventory_before_real_race}" >> "${CHAOS_RACE_REPORT}"
printf 'expected_consumption=%s\n' "${expected_consumption}" >> "${CHAOS_RACE_REPORT}"
printf 'expected_inventory_after=%s\n' "${expected_inventory_after}" >> "${CHAOS_RACE_REPORT}"
printf 'sample_sale_ids=%s\n' "${sample_sale_ids[*]:-}" >> "${CHAOS_RACE_REPORT}"
printf 'response_summary_200_201=%s\n' "${count_200_201}" >> "${CHAOS_RACE_REPORT}"
printf 'response_summary_409_INSUFFICIENT_STOCK=%s\n' "${count_409_insufficient}" >> "${CHAOS_RACE_REPORT}"
printf 'response_summary_400=%s\n' "${count_400}" >> "${CHAOS_RACE_REPORT}"
printf 'response_summary_401=%s\n' "${count_401}" >> "${CHAOS_RACE_REPORT}"
printf 'response_summary_403=%s\n' "${count_403}" >> "${CHAOS_RACE_REPORT}"
printf 'response_summary_404=%s\n' "${count_404}" >> "${CHAOS_RACE_REPORT}"
printf 'response_summary_409_total=%s\n' "${count_409_total}" >> "${CHAOS_RACE_REPORT}"
printf 'response_summary_500=%s\n' "${count_500}" >> "${CHAOS_RACE_REPORT}"

update_env_value "chaos_race_success_count" "${success_count}"
update_env_value "chaos_race_failure_count" "${failure_count}"
update_env_value "chaos_race_accepted_affecting_count" "${accepted_affecting_count}"
update_env_value "chaos_race_accepted_non_affecting_count" "${accepted_non_affecting_count}"
update_env_value "chaos_race_qty_per_sale" "${qty_per_sale}"
update_env_value "chaos_race_http_200_201_count" "${count_200_201}"
update_env_value "chaos_race_http_409_total_count" "${count_409_total}"
update_env_value "chaos_race_http_409_insufficient_stock_count" "${count_409_insufficient}"
update_env_value "chaos_race_http_400_count" "${count_400}"
update_env_value "chaos_race_http_401_count" "${count_401}"
update_env_value "chaos_race_http_403_count" "${count_403}"
update_env_value "chaos_race_http_404_count" "${count_404}"
update_env_value "chaos_race_http_500_count" "${count_500}"
update_env_value "chaos_race_api_code_insufficient_stock_count" "${API_COUNTS[INSUFFICIENT_STOCK]:-0}"
update_env_value "ledger_inventory_before_real_race" "${inventory_before_real_race}"
update_env_value "ledger_movements_before_real_race" "${movements_before_real_race}"
update_env_value "ledger_expected_consumption_after_race" "${expected_consumption}"
update_env_value "ledger_expected_inventory_after_race" "${expected_inventory_after}"
update_env_value "chaos_race_sample_sale_ids" "${sample_sale_ids[*]:-}"

ok "Chaos race concluído: ${success_count} sucesso(s), ${failure_count} falha(s)"
step "Sucessos que afetam inventory: ${accepted_affecting_count}"
step "Sucessos que NÃO afetam inventory: ${accepted_non_affecting_count}"
step "Resumo por código de resposta:"
detail "200/201 = ${count_200_201}"
detail "409 INSUFFICIENT_STOCK = ${count_409_insufficient}"
detail "400 = ${count_400}"
detail "401 = ${count_401}"
detail "403 = ${count_403}"
detail "404 = ${count_404}"
detail "409 total = ${count_409_total}"
detail "500 = ${count_500}"
detail "Arquivos de resumo:"
detail "${http_counts_file}"
detail "${api_counts_file}"
detail "${http_api_counts_file}"
hr

title "Verificando inventory e movements após chaos race"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --export-environment "${TEMP_ENV}" --folder "📦 92 - CHAOS RACE VERIFY" --reporters cli,json --reporter-json-export "${REPORT_DIR}/chaos-race-verify.json"
ok "Verificação pós-chaos race concluída"

actual_inventory_after="$(read_env_value inventory_after_chaos_race)"
actual_movements_after="$(read_env_value movements_after_chaos_race)"
[[ -n "${actual_inventory_after}" && "${actual_inventory_after}" != "null" ]] || actual_inventory_after=0
[[ -n "${actual_movements_after}" && "${actual_movements_after}" != "null" ]] || actual_movements_after=0
actual_consumption=$(( inventory_before_real_race - actual_inventory_after ))
movements_delta=$(( actual_movements_after - movements_before_real_race ))

update_env_value "ledger_inventory_after_real_race" "${actual_inventory_after}"
update_env_value "ledger_movements_after_real_race" "${actual_movements_after}"
update_env_value "ledger_actual_consumption_after_race" "${actual_consumption}"
update_env_value "ledger_movements_delta_after_race" "${movements_delta}"

if [[ "${actual_inventory_after}" != "${expected_inventory_after}" ]]; then
  err "Ledger mismatch: esperado ${expected_inventory_after}, atual ${actual_inventory_after}"
  update_env_value "ledger_race_result" "FAILED_LEDGER_MISMATCH"
  step "Dica: confira ${statuses_file} e ${worker_log_dir}/worker_*.log"
  exit 1
fi
if (( movements_delta < accepted_affecting_count )); then
  err "Movement delta inconsistente: delta=${movements_delta}, affecting_success_count=${accepted_affecting_count}"
  update_env_value "ledger_race_result" "FAILED_MOVEMENT_DELTA"
  step "Dica: confira ${statuses_file} e ${worker_log_dir}/worker_*.log"
  exit 1
fi
update_env_value "ledger_race_result" "PASSED"
step "Ledger expected inventory after race: ${expected_inventory_after}"
step "Ledger actual inventory after race: ${actual_inventory_after}"
step "Ledger expected consumption: ${expected_consumption}"
step "Ledger actual consumption: ${actual_consumption}"
step "Ledger movements delta: ${movements_delta}"
hr

title "Executando rollback probe"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --export-environment "${TEMP_ENV}" --folder "📦 93 - CHAOS ROLLBACK PROBE" --reporters cli,json --reporter-json-export "${REPORT_DIR}/chaos-rollback-probe.json"
ok "Rollback probe concluído"
hr

title "Resumo do chaos race test"
cat "${CHAOS_RACE_REPORT}"
hr

ok "TESTE V21.0 ULTRA CORRIGIDA FINALIZADO"
