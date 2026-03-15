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
success_banner() { echo -e "${GREEN}╔══════════════════════════════════════════════════╗${RESET}"; echo -e "${GREEN}║ $*${RESET}"; echo -e "${GREEN}╚══════════════════════════════════════════════════╝${RESET}"; }
error_banner() { echo -e "${RED}╔══════════════════════════════════════════════════╗${RESET}"; echo -e "${RED}║ $*${RESET}"; echo -e "${RED}╚══════════════════════════════════════════════════╝${RESET}"; }

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
LOG_FILE="${LOG_DIR}/execucao_v18.0_${TIMESTAMP}.log"
APP_LOG="${LOG_DIR}/app_${TIMESTAMP}.log"
REPORT_DIR="${LOG_DIR}/reports_${TIMESTAMP}"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v18.0.chaos-security.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v18.0.chaos-security.json"
TEMP_ENV="${SCRIPT_DIR}/.env.effective.json"
TEMP_NEWMAN="${SCRIPT_DIR}/.newman-report.json"
APP_PID=""

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"; }

cleanup() {
  local exit_code=$?
  title "Limpando recursos"
  log "Iniciando limpeza (exit code: $exit_code)"
  if [[ -n "${APP_PID:-}" ]]; then
    step "Parando aplicação (PID: $APP_PID)"
    kill "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" >/dev/null 2>&1 || true
    log "Aplicação parada"
  fi
  step "Removendo arquivos temporários"
  rm -f "${TEMP_ENV}" "${TEMP_NEWMAN}" 2>/dev/null || true
  [[ -L "${SCRIPT_DIR}/mvnw" ]] && rm -f "${SCRIPT_DIR}/mvnw"
  [[ -d "${SCRIPT_DIR}/.mvn" ]] && rm -rf "${SCRIPT_DIR}/.mvn"
  ok "Limpeza concluída"
  hr
  [[ $exit_code -ne 0 ]] && error_banner "EXECUÇÃO FALHOU (código: $exit_code)"
}
trap cleanup EXIT

echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V18.0 - CHAOS + SECURITY (STRICT SUITE)"
echo "   Iniciando execução em: $(date)"
echo "   Path do script: $SCRIPT_DIR"
echo "────────────────────────────────────────────────────"

title "Verificando requisitos do sistema"
log "Iniciando verificação de requisitos"
detail "Node.js: $(node --version 2>/dev/null || echo N/A)"
detail "Newman: $(newman --version 2>/dev/null || echo N/A)"
detail "PostgreSQL: $(psql --version 2>/dev/null || echo N/A)"
detail "curl: $(curl --version 2>/dev/null | head -1 || echo N/A)"
detail "Maven Wrapper: encontrado na raiz do projeto"
detail "Collection: $(basename "${COLLECTION}")"
detail "Environment: $(basename "${ENV_FILE}")"
[[ -f "${PROJECT_ROOT}/mvnw" ]] || { err "mvnw não encontrado na raiz"; exit 1; }
[[ -f "${COLLECTION}" ]] || { err "Collection não encontrada"; exit 1; }
[[ -f "${ENV_FILE}" ]] || { err "Environment não encontrado"; exit 1; }
success_banner "TODOS OS REQUISITOS OK"
hr

title "Verificando porta 8080"
log "Verificando disponibilidade da porta 8080"
if command -v netstat >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep -q ":${APP_PORT}.*LISTENING"; then
  warn "Porta $APP_PORT em uso, liberando..."
  pids=$(netstat -ano | grep ":${APP_PORT}" | grep LISTENING | awk '{print $5}')
  for pid in $pids; do taskkill //PID "$pid" //F >/dev/null 2>&1 || true; done
  sleep 2
fi
ok "Porta $APP_PORT disponível"
log "Porta $APP_PORT disponível"
hr

title "Resetando banco de dados"
log "Resetando banco de dados $DB_NAME"
export PGPASSWORD="${DB_PASSWORD}"
step "Terminando conexões ativas"
psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
step "Removendo banco de dados existente"
psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;" >/dev/null 2>&1
log "Banco $DB_NAME removido"
step "Criando novo banco de dados"
psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "CREATE DATABASE $DB_NAME;" >/dev/null 2>&1
ok "Banco de dados criado com sucesso"
log "Banco $DB_NAME criado"
hr

title "Preparando environment"
log "Preparando environment de teste"
cp "${ENV_FILE}" "${TEMP_ENV}"
step "Environment base copiado para: $TEMP_ENV"
if command -v jq >/dev/null 2>&1; then
  jq '.values = [.values[] | if .key == "base_url" then .value = "'"$BASE_URL"'" else . end]' "${TEMP_ENV}" > "${TEMP_ENV}.tmp"
  mv "${TEMP_ENV}.tmp" "${TEMP_ENV}"
fi
step "base_url ajustado para: $BASE_URL"
log "Environment preparado: $TEMP_ENV"
ok "Environment pronto"
hr

title "Criando links simbólicos"
log "Criando links simbólicos necessários"
ln -sf "${PROJECT_ROOT}/mvnw" "${SCRIPT_DIR}/mvnw"
step "Link criado: mvnw"
log "Link criado: $SCRIPT_DIR/mvnw -> $PROJECT_ROOT/mvnw"
if [[ -d "${PROJECT_ROOT}/.mvn" ]]; then
  cp -r "${PROJECT_ROOT}/.mvn" "${SCRIPT_DIR}/" 2>/dev/null || true
  step "Pasta .mvn copiada"
  log "Pasta .mvn copiada para $SCRIPT_DIR/.mvn"
fi
ok "Links prontos"
hr

title "Iniciando aplicação Spring Boot"
log "Iniciando aplicação"
step "Usando Maven Wrapper da raiz"
log "Comando: cd $PROJECT_ROOT && ./mvnw spring-boot:run"
: > "${APP_LOG}"
bash -c "cd '${PROJECT_ROOT}' && ./mvnw spring-boot:run" > "${APP_LOG}" 2>&1 &
APP_PID=$!
step "PID: $APP_PID"
step "Log: $APP_LOG"
log "Aplicação iniciada com PID $APP_PID"

info "Aguardando aplicação iniciar (timeout: $APP_START_TIMEOUT"s")"
log "Aguardando inicialização por até $APP_START_TIMEOUT"s""
for i in $(seq 1 "$APP_START_TIMEOUT"); do
  if grep -Eq "Started .*Application|Started .* in .* seconds|JVM running for" "${APP_LOG}" 2>/dev/null; then
    ok "Aplicação iniciada em ${i}s"
    log "Aplicação iniciada após ${i}s"
    break
  fi
  if (( i % 10 == 0 )); then
    lastline=$(tail -1 "${APP_LOG}" 2>/dev/null | cut -c1-70)
    step "Aguardando... (${i}s) Última linha: $lastline"
  fi
  sleep 1
  [[ $i -eq $APP_START_TIMEOUT ]] && { err "Timeout aguardando aplicação"; tail -50 "${APP_LOG}" || true; exit 1; }
done
hr

title "Health check"
step "URL: $BASE_URL/actuator/health"
log "Realizando health check: $BASE_URL/actuator/health"
curl -fsS "$BASE_URL/actuator/health" >/dev/null
ok "Health check OK (0s)"
log "Health check OK após 0s"
hr

title "Executando Newman - CHAOS + SECURITY STRICT"
log "Iniciando execução do Newman"
step "Collection: $(basename "${COLLECTION}")"
step "Environment: $(basename "${TEMP_ENV}")"
log "Comando Newman: newman run \"${COLLECTION}\" -e \"${TEMP_ENV}\" --export-environment \"${TEMP_ENV}\" --reporters cli,json --reporter-json-export \"${TEMP_NEWMAN}\" --timeout-request 30000 --timeout-script 30000"
newman run "${COLLECTION}" -e "${TEMP_ENV}" --export-environment "${TEMP_ENV}" --reporters cli,json --reporter-json-export "${TEMP_NEWMAN}" --timeout-request 30000 --timeout-script 30000
newman_exit=$?
log "Newman finalizado com código $newman_exit"
[[ $newman_exit -ne 0 ]] && exit $newman_exit
ok "Newman executado com sucesso"

if [[ -f "${TEMP_NEWMAN}" ]] && command -v jq >/dev/null 2>&1; then
  total_reqs=$(jq -r '.run.stats.requests.total // 0' "${TEMP_NEWMAN}")
  total_asserts=$(jq -r '.run.stats.assertions.total // 0' "${TEMP_NEWMAN}")
  total_fails=$(jq -r '.run.failures | length' "${TEMP_NEWMAN}")
  step "Requests: $total_reqs, Assertions: $total_asserts, Falhas: $total_fails"
  log "Estatísticas: requests=$total_reqs, assertions=$total_asserts, failures=$total_fails"
fi
hr

title "Extraindo tokens do environment"
log "Extraindo tokens gerados pelos testes"
tenant_token=$(jq -r '.values[] | select(.key=="tenant_access_token") | .value // ""' "${TEMP_ENV}" 2>/dev/null || echo "")
tenant_schema=$(jq -r '.values[] | select(.key=="tenant_schema") | .value // ""' "${TEMP_ENV}" 2>/dev/null || echo "")
tenant_account_id=$(jq -r '.values[] | select(.key=="tenant_account_id") | .value // ""' "${TEMP_ENV}" 2>/dev/null || echo "")
customer1_id=$(jq -r '.values[] | select(.key=="customer1_id") | .value // ""' "${TEMP_ENV}" 2>/dev/null || echo "")
customer2_id=$(jq -r '.values[] | select(.key=="customer2_id") | .value // ""' "${TEMP_ENV}" 2>/dev/null || echo "")
sale_id=$(jq -r '.values[] | select(.key=="sale_id") | .value // ""' "${TEMP_ENV}" 2>/dev/null || echo "")
step "Token Tenant: ${tenant_token:0:20}... (${#tenant_token} chars)"
step "Schema Tenant: $tenant_schema"
step "Account ID: $tenant_account_id"
step "Customer 1 ID: $customer1_id"
step "Customer 2 ID: $customer2_id"
step "Sale ID: $sale_id"
token_file="${REPORT_DIR}/tokens.txt"
cat > "${token_file}" << EOF
# Tokens gerados em $TIMESTAMP
TENANT_TOKEN=$tenant_token
TENANT_SCHEMA=$tenant_schema
TENANT_ACCOUNT_ID=$tenant_account_id
CUSTOMER1_ID=$customer1_id
CUSTOMER2_ID=$customer2_id
SALE_ID=$sale_id
EOF
step "Tokens salvos em: $token_file"
log "Tokens extraídos e salvos em $token_file"
ok "Extração concluída"
hr

title "Gerando relatório detalhado"
log "Gerando relatório de execução"
report_file="${REPORT_DIR}/resumo.txt"
min_rt=$(jq -r '[.run.executions[].response.responseTime | numbers] | min // 0' "${TEMP_NEWMAN}" 2>/dev/null || echo 0)
max_rt=$(jq -r '[.run.executions[].response.responseTime | numbers] | max // 0' "${TEMP_NEWMAN}" 2>/dev/null || echo 0)
mean_rt=$(jq -r '[.run.executions[].response.responseTime | numbers] | if length>0 then (add/length|floor) else 0 end' "${TEMP_NEWMAN}" 2>/dev/null || echo 0)
{
  echo "╔════════════════════════════════════════════════════════════╗"
  echo "║ RELATÓRIO DE TESTES V18.0 - CHAOS + SECURITY (STRICT) ║"
  echo "╚════════════════════════════════════════════════════════════╝"
  echo ""
  echo "Data da execução: $(date)"
  echo "Timestamp: $TIMESTAMP"
  echo "Diretório: $SCRIPT_DIR"
  echo ""
  echo "═══════════════════════════════════════════════════════════════"
  echo ""
  echo "📊 ESTATÍSTICAS GERAIS"
  echo "─────────────────────────────────"
  jq -r '"Requests totais: \(.run.stats.requests.total // 0)",
         "Assertions totais: \(.run.stats.assertions.total // 0)",
         "Falhas totais: \(.run.failures | length)",
         ""' "${TEMP_NEWMAN}"
  echo "⏱️  TEMPOS DE RESPOSTA"
  echo "─────────────────────────────────"
  echo "Mínimo: $min_rt ms"
  echo "Máximo: $max_rt ms"
  echo "Média: $mean_rt ms"
  echo ""
  echo "📋 ENDPOINTS MAIS LENTOS (TOP 10)"
  echo "─────────────────────────────────"
  jq -r '.run.executions[] | select(.response != null) | "\(.item.name)|\(.response.responseTime)"' "${TEMP_NEWMAN}" | sort -t'|' -k2 -nr | head -10 | while IFS='|' read -r name time; do
    printf "   %-50s %8sms\n" "${name:0:50}..." "$time"
  done
  echo ""
  echo "═══════════════════════════════════════════════════════════════"
  echo "Log completo: $LOG_FILE"
  echo "Tokens salvos em: $token_file"
  echo "═══════════════════════════════════════════════════════════════"
} > "${report_file}"
step "Relatório gerado: $report_file"
cat "${report_file}"
ok "Relatório pronto"
hr

success_banner "TESTES CONCLUÍDOS COM SUCESSO"
step "Tempo total de execução: concluído"
log "Tempo total: concluído"

title "📊 RESUMO DA EXECUÇÃO"
echo "Data/Hora: $(date)"
echo "Diretório: $SCRIPT_DIR"
echo "Log principal: $LOG_FILE"
echo "Relatórios: $REPORT_DIR"
echo ""
echo "✅ Relatório gerado com sucesso"
echo ""
step "Últimas 10 linhas do log:"
tail -10 "${LOG_FILE}" 2>/dev/null | sed 's/^/   /' || true
