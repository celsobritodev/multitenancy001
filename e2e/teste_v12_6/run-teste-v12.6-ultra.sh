#!/usr/bin/env bash
set -Eeuo pipefail

# =========================================================
# TESTE V12.6 - VERSÃO ULTRA (CUSTOMERS + SALES)
# =========================================================
# Características:
# - Múltiplos padrões de detecção de inicialização
# - Timeout configurável (padrão: 180s)
# - Logs detalhados em tempo real
# - Fallback para health check
# - Detecção automática de falhas
# - Testes específicos de customers e tenant isolation
# =========================================================

# Cores (com fallback para sistemas sem suporte)
if [[ -t 1 ]]; then
    RESET='\033[0m'
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    WHITE='\033[1;37m'
    CYAN='\033[0;36m'
    MAGENTA='\033[0;35m'
else
    RESET=''
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    WHITE=''
    CYAN=''
    MAGENTA=''
fi

# Funções de output
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

# =========================================================
# CONFIGURAÇÕES (podem ser sobrescritas por variáveis de ambiente)
# =========================================================

# Timeout para inicialização da aplicação (em segundos)
APP_START_TIMEOUT="${APP_START_TIMEOUT:-180}"

# Timeout para health check (em segundos)
HEALTH_CHECK_TIMEOUT="${HEALTH_CHECK_TIMEOUT:-60}"

# Porta da aplicação
APP_PORT="${APP_PORT:-8080}"

# URL base
BASE_URL="${BASE_URL:-http://localhost:${APP_PORT}}"

# Configurações do banco de dados
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-db_multitenancy}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-admin}"

# Modo debug (true/false)
DEBUG_MODE="${DEBUG_MODE:-false}"

# =========================================================
# CONFIGURAÇÕES FIXAS
# =========================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Timestamp para logs
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_DIR="${SCRIPT_DIR}/logs"
LOG_FILE="${LOG_DIR}/execucao_v12.6_${TIMESTAMP}.log"
APP_LOG="${LOG_DIR}/app_${TIMESTAMP}.log"
REPORT_DIR="${LOG_DIR}/reports_${TIMESTAMP}"
mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

# Arquivos de teste
COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v12.6.enterprise.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v12.6.json"

# Arquivos temporários
TEMP_ENV="${SCRIPT_DIR}/.env.effective.json"
TEMP_NEWMAN="${SCRIPT_DIR}/.newman-report.json"

# PID da aplicação
APP_PID=""

# =========================================================
# FUNÇÕES DE LOG
# =========================================================

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

log_debug() {
    if [[ "${DEBUG_MODE}" == "true" ]]; then
        echo "[DEBUG] $*" | tee -a "${LOG_FILE}"
    fi
}

log_error() {
    echo "[ERROR] $*" | tee -a "${LOG_FILE}" >&2
}

# =========================================================
# FUNÇÕES DE VERIFICAÇÃO
# =========================================================

check_requirements() {
    title "Verificando requisitos do sistema"
    log "Iniciando verificação de requisitos"
    
    local missing=0
    
    # Node.js
    if command -v node >/dev/null 2>&1; then
        local node_version=$(node --version)
        step "Node.js: ${node_version}"
        log "Node.js OK: ${node_version}"
    else
        err "Node.js não encontrado"
        log_error "Node.js não encontrado"
        missing=1
    fi
    
    # Newman
    if command -v newman >/dev/null 2>&1; then
        local newman_version=$(newman --version)
        step "Newman: ${newman_version}"
        log "Newman OK: ${newman_version}"
    else
        err "Newman não encontrado (instale com: npm install -g newman)"
        log_error "Newman não encontrado"
        missing=1
    fi
    
    # PostgreSQL
    if command -v psql >/dev/null 2>&1; then
        local psql_version=$(psql --version | head -1)
        step "PostgreSQL: ${psql_version}"
        log "PostgreSQL OK: ${psql_version}"
    else
        err "PostgreSQL (psql) não encontrado"
        log_error "psql não encontrado"
        missing=1
    fi
    
    # curl
    if command -v curl >/dev/null 2>&1; then
        local curl_version=$(curl --version | head -1)
        step "curl: ${curl_version}"
        log "curl OK: ${curl_version}"
    else
        err "curl não encontrado"
        log_error "curl não encontrado"
        missing=1
    fi
    
    # Maven
    if [[ -x "${PROJECT_ROOT}/mvnw" ]]; then
        step "Maven Wrapper: encontrado na raiz do projeto"
        log "Maven Wrapper OK: ${PROJECT_ROOT}/mvnw"
    elif command -v mvn >/dev/null 2>&1; then
        local mvn_version=$(mvn --version | head -1)
        step "Maven: ${mvn_version}"
        log "Maven OK: ${mvn_version}"
    else
        err "Maven não encontrado (nem mvnw nem mvn global)"
        log_error "Maven não encontrado"
        missing=1
    fi
    
    # Arquivos de teste
    step "Verificando arquivos de teste:"
    log "Verificando arquivos de teste"
    
    if [[ -f "${COLLECTION}" ]]; then
        detail "Collection: $(basename "${COLLECTION}")"
        log "Collection OK: ${COLLECTION}"
    else
        err "Collection não encontrada: ${COLLECTION}"
        log_error "Collection não encontrada: ${COLLECTION}"
        missing=1
    fi
    
    if [[ -f "${ENV_FILE}" ]]; then
        detail "Environment: $(basename "${ENV_FILE}")"
        log "Environment OK: ${ENV_FILE}"
    else
        err "Environment não encontrado: ${ENV_FILE}"
        log_error "Environment não encontrado: ${ENV_FILE}"
        missing=1
    fi
    
    if [[ $missing -eq 1 ]]; then
        error_banner "REQUISITOS NÃO ATENDIDOS"
        err "Corrija os erros acima e tente novamente"
        log_error "Verificação de requisitos falhou"
        exit 1
    fi
    
    success_banner "TODOS OS REQUISITOS OK"
    hr
}

check_port() {
    title "Verificando porta ${APP_PORT}"
    log "Verificando disponibilidade da porta ${APP_PORT}"
    
    if command -v netstat >/dev/null 2>&1; then
        if netstat -ano 2>/dev/null | grep -q ":${APP_PORT}.*LISTENING"; then
            warn "Porta ${APP_PORT} está em uso"
            log "Porta ${APP_PORT} em uso, tentando liberar"
            
            local pids
            pids=$(netstat -ano 2>/dev/null | grep ":${APP_PORT}" | grep "LISTENING" | awk '{print $5}' | sort -u | xargs)
            
            if [[ -n "${pids}" ]]; then
                step "Processos usando a porta: ${pids}"
                for pid in $pids; do
                    step "Matando processo PID: ${pid}"
                    log "Matando processo ${pid}"
                    taskkill //PID "${pid}" //F >/dev/null 2>&1 || true
                done
                sleep 2
            fi
            
            if netstat -ano 2>/dev/null | grep -q ":${APP_PORT}.*LISTENING"; then
                err "Não foi possível liberar a porta ${APP_PORT}"
                log_error "Falha ao liberar porta ${APP_PORT}"
                exit 1
            else
                ok "Porta ${APP_PORT} liberada"
                log "Porta ${APP_PORT} liberada com sucesso"
            fi
        else
            ok "Porta ${APP_PORT} disponível"
            log "Porta ${APP_PORT} disponível"
        fi
    else
        warn "netstat não encontrado, pulando verificação de porta"
        log "netstat não disponível, pulando verificação de porta"
    fi
    hr
}

# =========================================================
# FUNÇÕES DE BANCO DE DADOS
# =========================================================

reset_database() {
    title "Resetando banco de dados"
    log "Resetando banco de dados ${DB_NAME}"
    
    export PGPASSWORD="${DB_PASSWORD}"
    
    # Terminar conexões ativas
    step "Terminando conexões ativas"
    psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
        -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();" \
        >/dev/null 2>&1 || true
    
    # Dropar banco
    step "Removendo banco de dados existente"
    if psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
        -c "DROP DATABASE IF EXISTS ${DB_NAME};" >/dev/null 2>&1; then
        log "Banco ${DB_NAME} removido"
    else
        warn "Falha ao remover banco (pode não existir)"
        log "Falha ao remover banco ${DB_NAME}"
    fi
    
    # Criar banco
    step "Criando novo banco de dados"
    if psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres \
        -c "CREATE DATABASE ${DB_NAME};" >/dev/null 2>&1; then
        ok "Banco de dados criado com sucesso"
        log "Banco ${DB_NAME} criado"
    else
        err "Falha ao criar banco de dados"
        log_error "Falha ao criar banco ${DB_NAME}"
        exit 1
    fi
    
    hr
}

# =========================================================
# FUNÇÕES DA APLICAÇÃO
# =========================================================

prepare_environment() {
    title "Preparando environment"
    log "Preparando environment de teste"
    
    cp "${ENV_FILE}" "${TEMP_ENV}"
    step "Environment base copiado para: ${TEMP_ENV}"
    
    # Garantir que o base_url está correto
    if command -v jq >/dev/null 2>&1; then
        jq ".values = [.values[] | if .key == \"base_url\" then .value = \"${BASE_URL}\" else . end]" "${TEMP_ENV}" > "${TEMP_ENV}.tmp"
        mv "${TEMP_ENV}.tmp" "${TEMP_ENV}"
        step "base_url ajustado para: ${BASE_URL}"
    fi
    
    log "Environment preparado: ${TEMP_ENV}"
    ok "Environment pronto"
    hr
}

create_symlinks() {
    title "Criando links simbólicos"
    log "Criando links simbólicos necessários"
    
    if [[ ! -f "${SCRIPT_DIR}/mvnw" && -f "${PROJECT_ROOT}/mvnw" ]]; then
        ln -sf "${PROJECT_ROOT}/mvnw" "${SCRIPT_DIR}/mvnw"
        step "Link criado: mvnw"
        log "Link criado: ${SCRIPT_DIR}/mvnw -> ${PROJECT_ROOT}/mvnw"
    fi
    
    if [[ -d "${PROJECT_ROOT}/.mvn" && ! -d "${SCRIPT_DIR}/.mvn" ]]; then
        cp -r "${PROJECT_ROOT}/.mvn" "${SCRIPT_DIR}/" 2>/dev/null || true
        step "Pasta .mvn copiada"
        log "Pasta .mvn copiada para ${SCRIPT_DIR}/.mvn"
    fi
    
    ok "Links prontos"
    hr
}

start_application() {
    title "Iniciando aplicação Spring Boot"
    log "Iniciando aplicação"
    
    local start_cmd
    
    if [[ -x "${PROJECT_ROOT}/mvnw" ]]; then
        start_cmd="cd ${PROJECT_ROOT} && ./mvnw spring-boot:run"
        step "Usando Maven Wrapper da raiz"
        log "Comando: ${start_cmd}"
    elif command -v mvn >/dev/null 2>&1; then
        start_cmd="cd ${PROJECT_ROOT} && mvn spring-boot:run"
        step "Usando Maven global"
        log "Comando: ${start_cmd}"
    else
        err "Maven não encontrado"
        log_error "Maven não encontrado"
        exit 1
    fi
    
    : > "${APP_LOG}"
    
    bash -c "${start_cmd}" > "${APP_LOG}" 2>&1 &
    APP_PID=$!
    
    step "PID: ${APP_PID}"
    step "Log: ${APP_LOG}"
    log "Aplicação iniciada com PID ${APP_PID}"
    
    info "Aguardando aplicação iniciar (timeout: ${APP_START_TIMEOUT}s)"
    log "Aguardando inicialização por até ${APP_START_TIMEOUT}s"
    
    local patterns=(
        "Started .*Application"
        "Started .* in .* seconds"
        "JVM running for"
        "ApplicationStarted"
        "started on port"
    )
    
    local elapsed=0
    local last_line=""
    
    while [[ $elapsed -lt ${APP_START_TIMEOUT} ]]; do
        if ! kill -0 "${APP_PID}" >/dev/null 2>&1; then
            err "Processo da aplicação morreu inesperadamente"
            log_error "Processo ${APP_PID} morreu"
            step "Últimas 20 linhas do log:"
            tail -20 "${APP_LOG}" | sed 's/^/     /'
            exit 1
        fi
        
        for pattern in "${patterns[@]}"; do
            if grep -q "${pattern}" "${APP_LOG}" 2>/dev/null; then
                ok "Aplicação iniciada em ${elapsed}s (padrão: ${pattern})"
                log "Aplicação iniciada após ${elapsed}s (padrão: ${pattern})"
                
                step "Últimas 5 linhas do log:"
                tail -5 "${APP_LOG}" | sed 's/^/     /'
                
                hr
                return 0
            fi
        done
        
        if [[ $((elapsed % 10)) -eq 0 && $elapsed -gt 0 ]]; then
            last_line=$(tail -1 "${APP_LOG}" 2>/dev/null || echo "")
            step "Aguardando... (${elapsed}s) Última linha: ${last_line:0:60}"
        fi
        
        sleep 1
        elapsed=$((elapsed + 1))
    done
    
    err "Timeout aguardando inicialização da aplicação (${APP_START_TIMEOUT}s)"
    log_error "Timeout após ${APP_START_TIMEOUT}s"
    step "Últimas 50 linhas do log:"
    tail -50 "${APP_LOG}" | sed 's/^/     /'
    exit 1
}

health_check() {
    title "Health check"
    
    local health_url="${BASE_URL}/actuator/health"
    step "URL: ${health_url}"
    log "Realizando health check: ${health_url}"
    
    local elapsed=0
    while [[ $elapsed -lt ${HEALTH_CHECK_TIMEOUT} ]]; do
        if curl -fsS "${health_url}" >/dev/null 2>&1; then
            ok "Health check OK (${elapsed}s)"
            log "Health check OK após ${elapsed}s"
            hr
            return 0
        fi
        
        sleep 2
        elapsed=$((elapsed + 2))
    done
    
    err "Health check falhou após ${HEALTH_CHECK_TIMEOUT}s"
    log_error "Health check falhou"
    exit 1
}

# =========================================================
# FUNÇÕES DE TESTE
# =========================================================

run_newman() {
    title "Executando Newman - CUSTOMERS + SALES"
    log "Iniciando execução do Newman"
    
    step "Collection: $(basename "${COLLECTION}")"
    step "Environment: $(basename "${TEMP_ENV}")"
    
    local newman_cmd="newman run \"${COLLECTION}\" -e \"${TEMP_ENV}\" --export-environment \"${TEMP_ENV}\" --reporters cli,json --reporter-json-export \"${TEMP_NEWMAN}\" --timeout-request 30000 --timeout-script 30000"
    
    log "Comando Newman: ${newman_cmd}"
    
    set +e
    eval "${newman_cmd}" 2>&1 | tee -a "${LOG_FILE}"
    local exit_code=$?
    set -e
    
    log "Newman finalizado com código ${exit_code}"
    
    if [[ $exit_code -eq 0 ]]; then
        ok "Newman executado com sucesso"
        
        if [[ -f "${TEMP_NEWMAN}" ]] && command -v jq >/dev/null 2>&1; then
            local total_reqs=$(jq -r '.run.stats.requests.total // 0' "${TEMP_NEWMAN}" 2>/dev/null)
            local total_asserts=$(jq -r '.run.stats.assertions.total // 0' "${TEMP_NEWMAN}" 2>/dev/null)
            local total_fails=$(jq -r '.run.failures | length // 0' "${TEMP_NEWMAN}" 2>/dev/null)
            step "Requests: ${total_reqs}, Assertions: ${total_asserts}, Falhas: ${total_fails}"
            log "Estatísticas: requests=${total_reqs}, assertions=${total_asserts}, failures=${total_fails}"
        fi
    else
        err "Newman falhou (código: ${exit_code})"
        log_error "Newman falhou com código ${exit_code}"
    fi
    
    hr
    return $exit_code
}

extract_tokens() {
    title "Extraindo tokens do environment"
    log "Extraindo tokens gerados pelos testes"
    
    if [[ ! -f "${TEMP_ENV}" ]]; then
        warn "Environment não encontrado: ${TEMP_ENV}"
        log "Environment não encontrado para extração de tokens"
        return 0
    fi
    
    if command -v jq >/dev/null 2>&1; then
        local tenant_token=$(jq -r '.values[] | select(.key=="tenant_access_token") | .value' "${TEMP_ENV}" 2>/dev/null || echo "")
        local tenant_schema=$(jq -r '.values[] | select(.key=="tenant_schema") | .value' "${TEMP_ENV}" 2>/dev/null || echo "")
        local tenant_account_id=$(jq -r '.values[] | select(.key=="tenant_account_id") | .value' "${TEMP_ENV}" 2>/dev/null || echo "")
        local superadmin_token=$(jq -r '.values[] | select(.key=="superadmin_token") | .value' "${TEMP_ENV}" 2>/dev/null || echo "")
        local customer1_id=$(jq -r '.values[] | select(.key=="customer1_id") | .value' "${TEMP_ENV}" 2>/dev/null || echo "")
        local customer2_id=$(jq -r '.values[] | select(.key=="customer2_id") | .value' "${TEMP_ENV}" 2>/dev/null || echo "")
        local sale_id=$(jq -r '.values[] | select(.key=="sale_id") | .value' "${TEMP_ENV}" 2>/dev/null || echo "")
    else
        local tenant_token=$(grep -o '"tenant_access_token":"[^"]*"' "${TEMP_ENV}" | head -1 | cut -d'"' -f4 || echo "")
        local tenant_schema=$(grep -o '"tenant_schema":"[^"]*"' "${TEMP_ENV}" | head -1 | cut -d'"' -f4 || echo "")
        local tenant_account_id=$(grep -o '"tenant_account_id":"[^"]*"' "${TEMP_ENV}" | head -1 | cut -d'"' -f4 || echo "")
        local superadmin_token=$(grep -o '"superadmin_token":"[^"]*"' "${TEMP_ENV}" | head -1 | cut -d'"' -f4 || echo "")
        local customer1_id=$(grep -o '"customer1_id":"[^"]*"' "${TEMP_ENV}" | head -1 | cut -d'"' -f4 || echo "")
        local customer2_id=$(grep -o '"customer2_id":"[^"]*"' "${TEMP_ENV}" | head -1 | cut -d'"' -f4 || echo "")
        local sale_id=$(grep -o '"sale_id":"[^"]*"' "${TEMP_ENV}" | head -1 | cut -d'"' -f4 || echo "")
    fi
    
    step "Token Tenant: ${tenant_token:0:20}... (${#tenant_token} chars)"
    step "Schema Tenant: ${tenant_schema}"
    step "Account ID: ${tenant_account_id}"
    step "Customer 1 ID: ${customer1_id}"
    step "Customer 2 ID: ${customer2_id}"
    step "Sale ID: ${sale_id}"
    
    local token_file="${REPORT_DIR}/tokens.txt"
    cat > "${token_file}" << EOF
# Tokens gerados em ${TIMESTAMP}
TENANT_TOKEN=${tenant_token}
TENANT_SCHEMA=${tenant_schema}
TENANT_ACCOUNT_ID=${tenant_account_id}
SUPERADMIN_TOKEN=${superadmin_token}
CUSTOMER1_ID=${customer1_id}
CUSTOMER2_ID=${customer2_id}
SALE_ID=${sale_id}
EOF
    
    step "Tokens salvos em: ${token_file}"
    log "Tokens extraídos e salvos em ${token_file}"
    ok "Extração concluída"
    hr
}

generate_report() {
    title "Gerando relatório detalhado"
    log "Gerando relatório de execução"
    
    local report_file="${REPORT_DIR}/resumo.txt"
    
    {
        echo "╔════════════════════════════════════════════════════════════╗"
        echo "║      RELATÓRIO DE TESTES V12.6 - CUSTOMERS + SALES        ║"
        echo "╚════════════════════════════════════════════════════════════╝"
        echo ""
        echo "Data da execução: $(date)"
        echo "Timestamp: ${TIMESTAMP}"
        echo "Diretório: ${SCRIPT_DIR}"
        echo ""
        echo "═══════════════════════════════════════════════════════════════"
        echo ""
        
        if [[ -f "${TEMP_NEWMAN}" ]] && command -v jq >/dev/null 2>&1; then
            echo "📊 ESTATÍSTICAS GERAIS"
            echo "─────────────────────────────────"
            jq -r '
                "Requests totais: \(.run.stats.requests.total // 0)",
                "Assertions totais: \(.run.stats.assertions.total // 0)",
                "Falhas totais: \(.run.failures | length)",
                ""
            ' "${TEMP_NEWMAN}" 2>/dev/null || echo "Erro ao processar estatísticas"
            
            echo "⏱️  TEMPOS DE RESPOSTA"
            echo "─────────────────────────────────"
            jq -r '
                "Mínimo: \(.run.timings.min // 0)ms",
                "Máximo: \(.run.timings.max // 0)ms",
                "Média: \(.run.timings.mean // 0)ms",
                "Mediana: \(.run.timings.median // 0)ms",
                "P95: \(.run.timings.p95 // 0)ms",
                "P99: \(.run.timings.p99 // 0)ms",
                ""
            ' "${TEMP_NEWMAN}" 2>/dev/null || echo "Erro ao processar tempos"
            
            echo "📋 ENDPOINTS MAIS LENTOS (TOP 10)"
            echo "─────────────────────────────────"
            jq -r '.run.executions[]
                | select(.response != null)
                | "\(.item.name)|\(.response.responseTime)"
            ' "${TEMP_NEWMAN}" 2>/dev/null | sort -t'|' -k2 -nr | head -10 | while IFS='|' read name time; do
                printf "   %-50s %8sms\n" "${name:0:50}..." "${time}"
            done
        else
            echo "Arquivo do Newman não encontrado ou jq não disponível"
        fi
        
        echo ""
        echo "═══════════════════════════════════════════════════════════════"
        echo "Log completo: ${LOG_FILE}"
        echo "Tokens salvos em: ${REPORT_DIR}/tokens.txt"
        echo "═══════════════════════════════════════════════════════════════"
        
    } > "${report_file}"
    
    step "Relatório gerado: ${report_file}"
    
    if [[ -f "${report_file}" ]]; then
        cat "${report_file}"
    fi
    
    ok "Relatório pronto"
    hr
}

# =========================================================
# FUNÇÕES DE LIMPEZA
# =========================================================

cleanup() {
    local exit_code=$?
    
    title "Limpando recursos"
    log "Iniciando limpeza (exit code: ${exit_code})"
    
    if [[ -n "${APP_PID:-}" ]]; then
        step "Parando aplicação (PID: ${APP_PID})"
        kill "${APP_PID}" >/dev/null 2>&1 || true
        wait "${APP_PID}" >/dev/null 2>&1 || true
        log "Aplicação parada"
    fi
    
    step "Removendo arquivos temporários"
    local temp_files=(
        "${TEMP_ENV}"
        "${TEMP_NEWMAN}"
        "${SCRIPT_DIR}"/.e2e-app.log
        "${SCRIPT_DIR}"/.e2e-newman.out.log
    )
    
    for file in "${temp_files[@]}"; do
        if [[ -f "${file}" ]]; then
            rm -f "${file}"
            log_debug "Removido: ${file}"
        fi
    done
    
    if [[ -L "${SCRIPT_DIR}/mvnw" ]]; then
        rm -f "${SCRIPT_DIR}/mvnw"
        step "Link mvnw removido"
        log "Link mvnw removido"
    fi
    
    ok "Limpeza concluída"
    hr
    
    if [[ $exit_code -ne 0 ]]; then
        error_banner "EXECUÇÃO FALHOU (código: ${exit_code})"
        log_error "Execução falhou com código ${exit_code}"
    fi
}

print_summary() {
    title "📊 RESUMO DA EXECUÇÃO"
    
    echo "Data/Hora: $(date)"
    echo "Diretório: ${SCRIPT_DIR}"
    echo "Log principal: ${LOG_FILE}"
    echo "Relatórios: ${REPORT_DIR}"
    echo ""
    
    if [[ -f "${REPORT_DIR}/resumo.txt" ]]; then
        echo "✅ Relatório gerado com sucesso"
    else
        echo "❌ Relatório não encontrado"
    fi
    
    echo ""
    step "Últimas 10 linhas do log:"
    tail -10 "${LOG_FILE}" 2>/dev/null | sed 's/^/   /' || echo "   (log vazio)"
    
    hr
}

# =========================================================
# FUNÇÃO PRINCIPAL
# =========================================================

main() {
    local start_time=$(date +%s)
    local exit_code=0
    
    hr
    echo -e "${WHITE}🔷 TESTE V12.6 - CUSTOMERS + SALES (VERSÃO ULTRA ROBUSTA)${RESET}"
    echo -e "${WHITE}   Iniciando execução em: $(date)${RESET}"
    hr
    
    log "=== INÍCIO DA EXECUÇÃO ==="
    log "Script: ${0}"
    log "Diretório: ${SCRIPT_DIR}"
    log "Timestamp: ${TIMESTAMP}"
    
    trap cleanup EXIT
    
    check_requirements
    check_port
    reset_database
    prepare_environment
    create_symlinks
    start_application
    health_check
    
    if run_newman; then
        extract_tokens
        generate_report
        success_banner "TESTES CONCLUÍDOS COM SUCESSO"
        exit_code=0
    else
        error_banner "TESTES FALHARAM"
        exit_code=1
    fi
    
    local end_time=$(date +%s)
    local total_time=$((end_time - start_time))
    local minutes=$((total_time / 60))
    local seconds=$((total_time % 60))
    
    step "Tempo total de execução: ${minutes}m ${seconds}s"
    log "Tempo total: ${total_time}s"
    
    print_summary
    
    log "=== FIM DA EXECUÇÃO (código: ${exit_code}) ==="
    
    return $exit_code
}

# =========================================================
# EXECUÇÃO
# =========================================================

case "${1:-}" in
    --help|-h)
        echo "Uso: $0 [OPÇÃO]"
        echo ""
        echo "Opções:"
        echo "  --help, -h     Mostra esta ajuda"
        echo "  --debug        Ativa modo debug"
        echo "  --timeout N    Define timeout em segundos (padrão: 180)"
        echo ""
        echo "Variáveis de ambiente:"
        echo "  APP_START_TIMEOUT    Timeout para inicialização (padrão: 180)"
        echo "  HEALTH_CHECK_TIMEOUT Timeout para health check (padrão: 60)"
        echo "  DEBUG_MODE           Ativa debug (true/false)"
        echo "  DB_NAME              Nome do banco de dados"
        echo "  DB_USER              Usuário do banco"
        echo "  DB_PASSWORD          Senha do banco"
        exit 0
        ;;
    --debug)
        DEBUG_MODE="true"
        shift
        ;;
    --timeout)
        APP_START_TIMEOUT="${2:-180}"
        shift 2
        ;;
esac

main "$@"