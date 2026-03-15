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
APP_LOG="${LOG_DIR}/app_${TIMESTAMP}.log"
REPORT_DIR="${LOG_DIR}/reports_${TIMESTAMP}"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v25.3.deterministic-chaos-engine-patched.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v25.3.deterministic-chaos-engine-patched.json"
TEMP_ENV="${SCRIPT_DIR}/.env.effective.json"
TEMP_NEWMAN="${SCRIPT_DIR}/.newman-report.json"
CHAOS_RACE_REPORT="${SCRIPT_DIR}/.chaos-race-report.txt"
WORKER_SCRIPT="${SCRIPT_DIR}/chaos-race-worker-sale.sh"
AGGREGATOR_SCRIPT="${SCRIPT_DIR}/chaos-race-aggregate.py"
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

safe_int() {
  local raw="${1:-0}"
  raw="$(echo "$raw" | tr -cd '0-9-\n')"
  [[ -z "$raw" ]] && raw=0
  echo "$raw"
}

safe_sub() {
  local a="$(safe_int "${1:-0}")"
  local b="$(safe_int "${2:-0}")"
  echo $(( a - b ))
}

fetch_inventory_snapshot() {
  local product_id="$1"
  local inventory_json movements_json
  inventory_json="$(curl -fsS -H "Authorization: Bearer ${TOKEN}" -H "X-Tenant: ${SCHEMA}" "${BASE_URL_EFFECTIVE}/api/tenant/inventory/products/${product_id}")"
  movements_json="$(curl -fsS -H "Authorization: Bearer ${TOKEN}" -H "X-Tenant: ${SCHEMA}" "${BASE_URL_EFFECTIVE}/api/tenant/inventory/products/${product_id}/movements")"
  python - <<'PY' "$inventory_json" "$movements_json"
import json, sys
inv = json.loads(sys.argv[1])
mov = json.loads(sys.argv[2])
qty = 0
if isinstance(inv, dict):
    for k in ('quantityAvailable','quantity','availableQuantity'):
        if inv.get(k) is not None:
            try:
                qty = int(float(inv.get(k)))
                break
            except Exception:
                pass
count = 0
if isinstance(mov, list):
    count = len(mov)
elif isinstance(mov, dict):
    for k in ('content','items','data','results'):
        if isinstance(mov.get(k), list):
            count = len(mov.get(k))
            break
    else:
        count = int(mov.get('totalElements') or mov.get('total') or 0)
print(f"{qty}\t{count}")
PY
}

compute_metrics_from_file() {
  local input_file="$1"
  python - <<'PY' "$input_file"
import sys, math
vals=[]
with open(sys.argv[1], 'r', encoding='utf-8', errors='ignore') as f:
    for line in f:
        s=line.strip()
        if not s:
            continue
        try:
            vals.append(int(float(s)))
        except Exception:
            pass
if not vals:
    print('0\t0\t0\t0')
    raise SystemExit
vals.sort()
avg=round(sum(vals)/len(vals))
p95=vals[max(0, math.ceil(0.95*len(vals))-1)]
mx=vals[-1]
print(f"{len(vals)}\t{avg}\t{p95}\t{mx}")
PY
}

run_get_perf() {
  local url="$1"
  local iterations="$2"
  local outfile="$3"
  : > "$outfile"
  for i in $(seq 1 "$iterations"); do
    curl -sS -o /dev/null -w '%{time_total}\n' -H "Authorization: Bearer ${TOKEN}" -H "X-Tenant: ${SCHEMA}" "$url" >> "$outfile.raw"
  done
  python - <<'PY' "$outfile.raw" "$outfile"
import sys
raw, out = sys.argv[1], sys.argv[2]
with open(raw,'r',encoding='utf-8',errors='ignore') as f, open(out,'w',encoding='utf-8') as g:
    for line in f:
        try:
            ms=int(round(float(line.strip())*1000))
        except Exception:
            ms=0
        g.write(str(ms)+'\n')
PY
  rm -f "$outfile.raw"
}

run_login_perf() {
  local iterations="$1"
  local outfile="$2"
  local email password body
  email="$(read_env_value tenant1_email)"
  [[ -n "$email" && "$email" != "null" ]] || email="$(read_env_value tenant_email)"
  password="$(read_env_value tenant1_password)"
  [[ -n "$password" && "$password" != "null" ]] || password="$(read_env_value tenant_password)"
  : > "$outfile"
  for i in $(seq 1 "$iterations"); do
    body=$(printf '{"email":"%s","password":"%s"}' "$email" "$password")
    curl -sS -o /dev/null -w '%{time_total}\n' -H 'Content-Type: application/json' -X POST "${BASE_URL_EFFECTIVE}/api/tenant/auth/login" --data "$body" >> "$outfile.raw"
  done
  python - <<'PY' "$outfile.raw" "$outfile"
import sys
raw, out = sys.argv[1], sys.argv[2]
with open(raw,'r',encoding='utf-8',errors='ignore') as f, open(out,'w',encoding='utf-8') as g:
    for line in f:
        try:
            ms=int(round(float(line.strip())*1000))
        except Exception:
            ms=0
        g.write(str(ms)+'\n')
PY
  rm -f "$outfile.raw"
}

echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V25.3 DETERMINISTIC CHAOS ENGINE PATCHED (ULTRA SUITE)"
echo "   Iniciando execução em: $(date)"
echo "   Path do script: $SCRIPT_DIR"
echo "────────────────────────────────────────────────────"

title "Verificando requisitos"
[[ -f "${COLLECTION}" ]] || { err "Collection não encontrada"; exit 1; }
[[ -f "${ENV_FILE}" ]] || { err "Environment não encontrado"; exit 1; }
[[ -f "${PROJECT_ROOT}/mvnw" ]] || { err "mvnw não encontrado na raiz"; exit 1; }
[[ -x "${WORKER_SCRIPT}" ]] || { err "Worker script não encontrado/executável: ${WORKER_SCRIPT}"; exit 1; }
[[ -x "${AGGREGATOR_SCRIPT}" ]] || { err "Aggregator script não encontrado/executável: ${AGGREGATOR_SCRIPT}"; exit 1; }
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

title "Executando suíte base V25.2"
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
PERF_AVG_LIMIT_MS="$(read_env_value perf_avg_limit_ms)"
PERF_P95_LIMIT_MS="$(read_env_value perf_p95_limit_ms)"
PERF_MAX_LIMIT_MS="$(read_env_value perf_max_limit_ms)"
PERF_INVENTORY_ITERS="$(read_env_value perf_inventory_read_iterations)"
PERF_MOVEMENTS_ITERS="$(read_env_value perf_movements_read_iterations)"
PERF_LOGIN_ITERS="$(read_env_value perf_login_iterations)"

[[ -n "${PARALLELISM}" && "${PARALLELISM}" != "null" ]] || PARALLELISM="100"
[[ -n "${ATTEMPTS}" && "${ATTEMPTS}" != "null" ]] || ATTEMPTS="100"
[[ -n "${MAX_JITTER_MS}" && "${MAX_JITTER_MS}" != "null" ]] || MAX_JITTER_MS="250"
[[ -n "${BASE_DELAY_MS}" && "${BASE_DELAY_MS}" != "null" ]] || BASE_DELAY_MS="20"
[[ -n "${RETRY_COUNT}" && "${RETRY_COUNT}" != "null" ]] || RETRY_COUNT="2"
[[ -n "${UNIT_PRICE}" && "${UNIT_PRICE}" != "null" ]] || UNIT_PRICE="1999.99"
[[ -n "${PRODUCT_NAME}" && "${PRODUCT_NAME}" != "null" ]] || PRODUCT_NAME="Smartphone XYZ"
[[ -n "${SALE_STATUS}" && "${SALE_STATUS}" != "null" ]] || SALE_STATUS="OPEN"
[[ -n "${PERF_AVG_LIMIT_MS}" && "${PERF_AVG_LIMIT_MS}" != "null" ]] || PERF_AVG_LIMIT_MS="250"
[[ -n "${PERF_P95_LIMIT_MS}" && "${PERF_P95_LIMIT_MS}" != "null" ]] || PERF_P95_LIMIT_MS="800"
[[ -n "${PERF_MAX_LIMIT_MS}" && "${PERF_MAX_LIMIT_MS}" != "null" ]] || PERF_MAX_LIMIT_MS="2500"
[[ -n "${PERF_INVENTORY_ITERS}" && "${PERF_INVENTORY_ITERS}" != "null" ]] || PERF_INVENTORY_ITERS="40"
[[ -n "${PERF_MOVEMENTS_ITERS}" && "${PERF_MOVEMENTS_ITERS}" != "null" ]] || PERF_MOVEMENTS_ITERS="40"
[[ -n "${PERF_LOGIN_ITERS}" && "${PERF_LOGIN_ITERS}" != "null" ]] || PERF_LOGIN_ITERS="25"

SALE_AFFECTS_INVENTORY="$(inventory_affecting_status "$SALE_STATUS")"
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

statuses_file="${REPORT_DIR}/chaos-race-statuses.tsv"
chaos_elapsed_file="${REPORT_DIR}/chaos-elapsed-ms.txt"
worker_log_dir="${REPORT_DIR}/workers"
agg_json="${REPORT_DIR}/chaos-aggregate.json"
chaos_run_id="v25.3-$(date +%s)-$$"
mkdir -p "$worker_log_dir"
: > "$statuses_file"
: > "$chaos_elapsed_file"
update_env_value chaos_run_id "${chaos_run_id}"

title "Capturando baseline real imediatamente antes do chaos"
read -r inventory_before_real_race movements_before_real_race < <(fetch_inventory_snapshot "${PRODUCT_ID}")
step "Inventory real antes do chaos: ${inventory_before_real_race}"
step "Movements reais antes do chaos: ${movements_before_real_race}"
hr

title "Disparando ${ATTEMPTS} vendas simultâneas reais com chaos"
pids=()
for i in $(seq 1 "${ATTEMPTS}"); do
  (
    result="$(${WORKER_SCRIPT} "${BASE_URL_EFFECTIVE}" "${TOKEN}" "${SCHEMA}" "${CUSTOMER_ID}" "${PRODUCT_ID}" "${UNIT_PRICE}" "${PRODUCT_NAME}" "${MAX_JITTER_MS}" "${BASE_DELAY_MS}" "${RETRY_COUNT}" "${SALE_STATUS}" "$i" "${worker_log_dir}" "${chaos_run_id}")"
    echo "$result" >> "$statuses_file"
  ) &
  pids+=($!)
  while [[ "$(jobs -rp | wc -l | tr -d ' ')" -ge "${PARALLELISM}" ]]; do sleep 0.03; done
done
for pid in "${pids[@]}"; do wait "$pid" || true; done

title "Consolidando resultados do chaos race"
python "${AGGREGATOR_SCRIPT}"   --statuses "${statuses_file}"   --worker-dir "${worker_log_dir}"   --output-json "${agg_json}"   --elapsed-out "${chaos_elapsed_file}"
ok "Agregação consolidada gerada"

success_count="$(jq -r '.success_count // 0' "${agg_json}")"
failure_count="$(jq -r '.failure_count // 0' "${agg_json}")"
accepted_affecting_count="$(jq -r '.accepted_affecting_count // 0' "${agg_json}")"
accepted_non_affecting_count="$(jq -r '.accepted_non_affecting_count // 0' "${agg_json}")"
rollback_like_count="$(jq -r '.rollback_like_count // 0' "${agg_json}")"
count_200_201="$(jq -r '.count_200_201 // 0' "${agg_json}")"
count_400="$(jq -r '.count_400 // 0' "${agg_json}")"
count_401="$(jq -r '.count_401 // 0' "${agg_json}")"
count_403="$(jq -r '.count_403 // 0' "${agg_json}")"
count_404="$(jq -r '.count_404 // 0' "${agg_json}")"
count_409_total="$(jq -r '.count_409_total // 0' "${agg_json}")"
count_409_insufficient="$(jq -r '.count_409_insufficient // 0' "${agg_json}")"
count_500="$(jq -r '.count_500 // 0' "${agg_json}")"
sample_sale_ids="$(jq -r '.sample_sale_ids // [] | join(" ")' "${agg_json}")"
agg_source="$(jq -r '.source_of_truth // "unknown"' "${agg_json}")"
agg_worker_rows="$(jq -r '.worker_rows_count // 0' "${agg_json}")"
agg_status_rows="$(jq -r '.status_rows_count // 0' "${agg_json}")"
agg_final_rows="$(jq -r '.final_rows_count // 0' "${agg_json}")"
agg_warnings="$(jq -r '.warnings // [] | join(",")' "${agg_json}")"
agg_parser_version="$(jq -r '.parser_version // "unknown"' "${agg_json}")"
agg_attempt_rows="$(jq -r '.attempt_rows_count // 0' "${agg_json}")"
agg_sample_correlation_ids="$(jq -r '.sample_correlation_ids // [] | join(" ")' "${agg_json}")"
agg_monotonic_valid="$(jq -r '.monotonic_timestamps_valid // false' "${agg_json}")"
agg_corr_complete="$(jq -r '.correlation_ids_complete // false' "${agg_json}")"
agg_corr_unique="$(jq -r '.correlation_ids_unique // false' "${agg_json}")"
final_lat_avg="$(jq -r '.final_latency_metrics_ms.avg // 0' "${agg_json}")"
final_lat_p95="$(jq -r '.final_latency_metrics_ms.p95 // 0' "${agg_json}")"
final_lat_max="$(jq -r '.final_latency_metrics_ms.max // 0' "${agg_json}")"
mono_lat_avg="$(jq -r '.monotonic_latency_metrics_ms.avg // 0' "${agg_json}")"
mono_lat_p95="$(jq -r '.monotonic_latency_metrics_ms.p95 // 0' "${agg_json}")"
mono_lat_max="$(jq -r '.monotonic_latency_metrics_ms.max // 0' "${agg_json}")"
worker_total_avg="$(jq -r '.worker_total_metrics_ms.avg // 0' "${agg_json}")"
worker_total_p95="$(jq -r '.worker_total_metrics_ms.p95 // 0' "${agg_json}")"
worker_total_max="$(jq -r '.worker_total_metrics_ms.max // 0' "${agg_json}")"
attempt_lat_avg="$(jq -r '.attempt_latency_metrics_ms.avg // 0' "${agg_json}")"
attempt_lat_p95="$(jq -r '.attempt_latency_metrics_ms.p95 // 0' "${agg_json}")"
attempt_lat_max="$(jq -r '.attempt_latency_metrics_ms.max // 0' "${agg_json}")"
retry_used_avg="$(jq -r '.retry_used_metrics.avg // 0' "${agg_json}")"
retry_used_p95="$(jq -r '.retry_used_metrics.p95 // 0' "${agg_json}")"
retry_used_max="$(jq -r '.retry_used_metrics.max // 0' "${agg_json}")"
update_env_value chaos_aggregate_source "${agg_source}"
update_env_value chaos_worker_rows_count "${agg_worker_rows}"
update_env_value chaos_status_rows_count "${agg_status_rows}"
update_env_value chaos_final_rows_count "${agg_final_rows}"
update_env_value chaos_attempt_rows_count "${agg_attempt_rows}"
update_env_value chaos_aggregate_warnings "${agg_warnings}"
update_env_value chaos_aggregate_parser_version "${agg_parser_version}"
update_env_value chaos_sample_correlation_ids "${agg_sample_correlation_ids}"
update_env_value chaos_monotonic_timestamps_valid "${agg_monotonic_valid}"
update_env_value chaos_correlation_ids_complete "${agg_corr_complete}"
update_env_value chaos_correlation_ids_unique "${agg_corr_unique}"
update_env_value chaos_final_latency_avg_ms "${final_lat_avg}"
update_env_value chaos_final_latency_p95_ms "${final_lat_p95}"
update_env_value chaos_final_latency_max_ms "${final_lat_max}"
update_env_value chaos_monotonic_latency_avg_ms "${mono_lat_avg}"
update_env_value chaos_monotonic_latency_p95_ms "${mono_lat_p95}"
update_env_value chaos_monotonic_latency_max_ms "${mono_lat_max}"
update_env_value chaos_worker_total_avg_ms "${worker_total_avg}"
update_env_value chaos_worker_total_p95_ms "${worker_total_p95}"
update_env_value chaos_worker_total_max_ms "${worker_total_max}"
update_env_value chaos_attempt_latency_avg_ms "${attempt_lat_avg}"
update_env_value chaos_attempt_latency_p95_ms "${attempt_lat_p95}"
update_env_value chaos_attempt_latency_max_ms "${attempt_lat_max}"
update_env_value chaos_retry_used_avg "${retry_used_avg}"
update_env_value chaos_retry_used_p95 "${retry_used_p95}"
update_env_value chaos_retry_used_max "${retry_used_max}"

expected_consumption=$(( accepted_affecting_count * 1 ))
expected_inventory_after=$(( inventory_before_real_race - expected_consumption ))

read -r debug_post_inventory debug_post_movements < <(fetch_inventory_snapshot "${PRODUCT_ID}")
step "DEBUG_POST inventory=${debug_post_inventory} movements=${debug_post_movements}"
step "Aggregator source=${agg_source} workerRows=${agg_worker_rows} statusRows=${agg_status_rows} attemptRows=${agg_attempt_rows} finalRows=${agg_final_rows}"
step "Parser version=${agg_parser_version} monotonicValid=${agg_monotonic_valid} correlationComplete=${agg_corr_complete} correlationUnique=${agg_corr_unique}"
[[ -n "${agg_sample_correlation_ids}" ]] && step "Sample correlationIds=${agg_sample_correlation_ids}"
[[ -n "${agg_warnings}" ]] && warn "Aggregator warnings: ${agg_warnings}"
hr

title "Verificando inventory e movements após chaos race"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --export-environment "${TEMP_ENV}" --folder "📦 92 - CHAOS RACE VERIFY" --reporters cli,json --reporter-json-export "${REPORT_DIR}/chaos-race-verify.json"
ok "Verificação pós-chaos race concluída"

actual_inventory_after="$(read_env_value inventory_after_chaos_race)"
actual_movements_after="$(read_env_value movements_after_chaos_race)"
[[ -n "${actual_inventory_after}" && "${actual_inventory_after}" != "null" ]] || actual_inventory_after="$debug_post_inventory"
[[ -n "${actual_movements_after}" && "${actual_movements_after}" != "null" ]] || actual_movements_after="$debug_post_movements"
actual_consumption=$(safe_sub "$inventory_before_real_race" "$actual_inventory_after")
movements_delta=$(safe_sub "$actual_movements_after" "$movements_before_real_race")
read -r chaos_n chaos_avg chaos_p95 chaos_max < <(compute_metrics_from_file "$chaos_elapsed_file")

if [[ "${SALE_AFFECTS_INVENTORY}" == "1" ]] && (( accepted_non_affecting_count > 0 )); then
  accepted_non_affecting_count=0
fi

if (( agg_final_rows == 0 )); then
  err "Agregador não consolidou nenhuma linha final do chaos race"
  update_env_value final_suite_result FAILED_AGGREGATOR_EMPTY
  exit 1
fi

if [[ "${agg_source}" != "worker_logs" ]]; then
  err "Agregador não conseguiu usar worker_logs como source of truth (source=${agg_source})"
  update_env_value final_suite_result FAILED_AGGREGATOR_SOURCE
  exit 1
fi

if (( agg_worker_rows < ATTEMPTS )); then
  err "Agregador consolidou apenas ${agg_worker_rows}/${ATTEMPTS} worker rows"
  update_env_value final_suite_result FAILED_AGGREGATOR_INCOMPLETE
  exit 1
fi

if [[ "${agg_monotonic_valid}" != "true" ]]; then
  err "Agregador detectou monotonic timestamps inválidos"
  update_env_value final_suite_result FAILED_MONOTONIC_TIMESTAMPS
  exit 1
fi

if [[ "${agg_corr_complete}" != "true" || "${agg_corr_unique}" != "true" ]]; then
  err "Agregador detectou correlationIds incompletos ou duplicados"
  update_env_value final_suite_result FAILED_CORRELATION_IDS
  exit 1
fi

ledger_summary_file="${REPORT_DIR}/ledger-summary.txt"
{
  echo "LEDGER SUMMARY"
  echo "inventory_before_real_race=${inventory_before_real_race}"
  echo "movements_before_real_race=${movements_before_real_race}"
  echo "accepted_affecting_count=${accepted_affecting_count}"
  echo "accepted_non_affecting_count=${accepted_non_affecting_count}"
  echo "expected_consumption=${expected_consumption}"
  echo "expected_inventory_after=${expected_inventory_after}"
  echo "actual_inventory_after=${actual_inventory_after}"
  echo "actual_consumption=${actual_consumption}"
  echo "actual_movements_after=${actual_movements_after}"
  echo "movements_delta=${movements_delta}"
  echo "response_200_201=${count_200_201}"
  echo "response_409_total=${count_409_total}"
  echo "response_409_insufficient_stock=${count_409_insufficient}"
  echo "response_400=${count_400}"
  echo "response_401=${count_401}"
  echo "response_403=${count_403}"
  echo "response_404=${count_404}"
  echo "response_500=${count_500}"
  echo "chaos_perf_avg_ms=${chaos_avg}"
  echo "chaos_perf_p95_ms=${chaos_p95}"
  echo "chaos_perf_max_ms=${chaos_max}"
  echo "final_latency_avg_ms=${final_lat_avg}"
  echo "final_latency_p95_ms=${final_lat_p95}"
  echo "final_latency_max_ms=${final_lat_max}"
  echo "monotonic_latency_avg_ms=${mono_lat_avg}"
  echo "monotonic_latency_p95_ms=${mono_lat_p95}"
  echo "monotonic_latency_max_ms=${mono_lat_max}"
  echo "worker_total_avg_ms=${worker_total_avg}"
  echo "worker_total_p95_ms=${worker_total_p95}"
  echo "worker_total_max_ms=${worker_total_max}"
  echo "attempt_latency_avg_ms=${attempt_lat_avg}"
  echo "attempt_latency_p95_ms=${attempt_lat_p95}"
  echo "attempt_latency_max_ms=${attempt_lat_max}"
  echo "retry_used_avg=${retry_used_avg}"
  echo "retry_used_p95=${retry_used_p95}"
  echo "retry_used_max=${retry_used_max}"
} > "$ledger_summary_file"

title "Relatório de reconciliação"
step "Arquivo ledger summary: ${ledger_summary_file}"
step "Inventory before race: ${inventory_before_real_race}"
step "Accepted affecting count: ${accepted_affecting_count}"
step "Expected inventory after race: ${expected_inventory_after}"
step "Actual inventory after race: ${actual_inventory_after}"
step "Actual consumption after race: ${actual_consumption}"
step "Movements delta after race: ${movements_delta}"
step "Chaos AVG/P95/MAX (ms): ${chaos_avg}/${chaos_p95}/${chaos_max}"

(( actual_inventory_after < 0 )) && { err "Oversell real detectado: inventory final negativo (${actual_inventory_after})"; update_env_value final_suite_result FAILED_OVERSELL_NEGATIVE_INVENTORY; exit 1; }
(( actual_consumption > inventory_before_real_race )) && { err "Oversell real detectado: consumo real (${actual_consumption}) excede saldo inicial (${inventory_before_real_race})"; update_env_value final_suite_result FAILED_OVERSELL_CONSUMPTION_EXCEEDED; exit 1; }
[[ "$actual_inventory_after" != "$expected_inventory_after" ]] && { err "Ledger mismatch: esperado ${expected_inventory_after}, atual ${actual_inventory_after}"; update_env_value final_suite_result FAILED_LEDGER_MISMATCH; exit 1; }
(( movements_delta < accepted_affecting_count )) && { err "Movement delta inconsistente: delta=${movements_delta}, affecting_success_count=${accepted_affecting_count}"; update_env_value final_suite_result FAILED_MOVEMENT_DELTA; exit 1; }
hr

title "Executando rollback probe"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --export-environment "${TEMP_ENV}" --folder "📦 93 - CHAOS ROLLBACK PROBE" --reporters cli,json --reporter-json-export "${REPORT_DIR}/chaos-rollback-probe.json"
ok "Rollback probe concluído"
hr

title "Performance reads / login"
inv_perf_file="${REPORT_DIR}/perf-inventory-ms.txt"
mov_perf_file="${REPORT_DIR}/perf-movements-ms.txt"
login_perf_file="${REPORT_DIR}/perf-login-ms.txt"
run_get_perf "${BASE_URL_EFFECTIVE}/api/tenant/inventory/products/${PRODUCT_ID}" "${PERF_INVENTORY_ITERS}" "$inv_perf_file"
run_get_perf "${BASE_URL_EFFECTIVE}/api/tenant/inventory/products/${PRODUCT_ID}/movements" "${PERF_MOVEMENTS_ITERS}" "$mov_perf_file"
run_login_perf "${PERF_LOGIN_ITERS}" "$login_perf_file"
read -r inv_n inv_avg inv_p95 inv_max < <(compute_metrics_from_file "$inv_perf_file")
read -r mov_n mov_avg mov_p95 mov_max < <(compute_metrics_from_file "$mov_perf_file")
read -r log_n log_avg log_p95 log_max < <(compute_metrics_from_file "$login_perf_file")
step "Inventory AVG/P95/MAX: ${inv_avg}/${inv_p95}/${inv_max} ms"
step "Movements AVG/P95/MAX: ${mov_avg}/${mov_p95}/${mov_max} ms"
step "Login AVG/P95/MAX: ${log_avg}/${log_p95}/${log_max} ms"
step "Thresholds AVG/P95/MAX: ${PERF_AVG_LIMIT_MS}/${PERF_P95_LIMIT_MS}/${PERF_MAX_LIMIT_MS} ms"
perf_fail=0
for metric in "$inv_avg:$PERF_AVG_LIMIT_MS:Inventory AVG" "$inv_p95:$PERF_P95_LIMIT_MS:Inventory P95" "$mov_avg:$PERF_AVG_LIMIT_MS:Movements AVG" "$mov_p95:$PERF_P95_LIMIT_MS:Movements P95" "$log_avg:$PERF_AVG_LIMIT_MS:Login AVG" "$log_p95:$PERF_P95_LIMIT_MS:Login P95"; do
  IFS=: read -r value limit label <<< "$metric"
  if (( value > limit )); then err "$label acima do threshold (${value}ms > ${limit}ms)"; perf_fail=1; fi
done
(( perf_fail != 0 )) && { update_env_value perf_result FAILED_THRESHOLDS; update_env_value final_suite_result FAILED_PERF_THRESHOLDS; err "Performance thresholds falharam"; exit 1; }
update_env_value perf_result PASSED
update_env_value final_suite_result PASSED
hr

{
  echo "attempts=${ATTEMPTS}"
  echo "parallelism=${PARALLELISM}"
  echo "sale_status=${SALE_STATUS}"
  echo "sale_status_affects_inventory=${SALE_AFFECTS_INVENTORY}"
  echo "max_jitter_ms=${MAX_JITTER_MS}"
  echo "base_delay_ms=${BASE_DELAY_MS}"
  echo "retry_count=${RETRY_COUNT}"
  echo "aggregate_source=${agg_source}"
  echo "aggregate_worker_rows=${agg_worker_rows}"
  echo "aggregate_status_rows=${agg_status_rows}"
  echo "aggregate_final_rows=${agg_final_rows}"
  echo "aggregate_warnings=${agg_warnings}"
  echo "aggregate_parser_version=${agg_parser_version}"
  echo "attempt_rows_count=${agg_attempt_rows}"
  echo "sample_correlation_ids=${agg_sample_correlation_ids}"
  echo "monotonic_timestamps_valid=${agg_monotonic_valid}"
  echo "correlation_ids_complete=${agg_corr_complete}"
  echo "correlation_ids_unique=${agg_corr_unique}"
  echo "success_count=${success_count}"
  echo "failure_count=${failure_count}"
  echo "accepted_affecting_count=${accepted_affecting_count}"
  echo "accepted_non_affecting_count=${accepted_non_affecting_count}"
  echo "rollback_like_count=${rollback_like_count}"
  echo "inventory_before_real_race=${inventory_before_real_race}"
  echo "expected_consumption=${expected_consumption}"
  echo "expected_inventory_after=${expected_inventory_after}"
  echo "sample_sale_ids=${sample_sale_ids:-}"
  echo "response_summary_200_201=${count_200_201}"
  echo "response_summary_409_INSUFFICIENT_STOCK=${count_409_insufficient}"
  echo "response_summary_400=${count_400}"
  echo "response_summary_401=${count_401}"
  echo "response_summary_403=${count_403}"
  echo "response_summary_404=${count_404}"
  echo "response_summary_409_total=${count_409_total}"
  echo "response_summary_500=${count_500}"
  echo "chaos_perf_avg_ms=${chaos_avg}"
  echo "chaos_perf_p95_ms=${chaos_p95}"
  echo "chaos_perf_max_ms=${chaos_max}"
  echo "final_latency_avg_ms=${final_lat_avg}"
  echo "final_latency_p95_ms=${final_lat_p95}"
  echo "final_latency_max_ms=${final_lat_max}"
  echo "monotonic_latency_avg_ms=${mono_lat_avg}"
  echo "monotonic_latency_p95_ms=${mono_lat_p95}"
  echo "monotonic_latency_max_ms=${mono_lat_max}"
  echo "worker_total_avg_ms=${worker_total_avg}"
  echo "worker_total_p95_ms=${worker_total_p95}"
  echo "worker_total_max_ms=${worker_total_max}"
  echo "attempt_latency_avg_ms=${attempt_lat_avg}"
  echo "attempt_latency_p95_ms=${attempt_lat_p95}"
  echo "attempt_latency_max_ms=${attempt_lat_max}"
  echo "retry_used_avg=${retry_used_avg}"
  echo "retry_used_p95=${retry_used_p95}"
  echo "retry_used_max=${retry_used_max}"
} > "$CHAOS_RACE_REPORT"

title "Resumo do chaos race test"
cat "$CHAOS_RACE_REPORT"
hr

update_env_value final_suite_result PASSED
update_env_value ledger_race_result PASSED
ok "STATUS FINAL: PASS com reconciliação consistente"
hr
title "V25.3 - Observação"
detail "A V25.3 mantém tudo da V25.2 e evolui o Deterministic Chaos Engine com correlationId, monotonic timestamp, retry tracing e métricas por worker."
detail "Runner ultra corrigido com correlação determinística por tentativa, monotonic timing end-to-end e telemetria por worker sem depender de heurística defensiva."