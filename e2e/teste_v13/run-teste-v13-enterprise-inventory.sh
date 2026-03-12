#!/usr/bin/env bash
set -Eeuo pipefail

# =========================================================
# TESTE V13.0 - VERSÃO SIMPLES (ENTERPRISE + INVENTORY)
# =========================================================

# Cores
RESET='\033[0m'; GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; WHITE='\033[1;37m'; CYAN='\033[0;36m'

ok() { echo -e "${GREEN}✅ $*${RESET}"; }
err() { echo -e "${RED}❌ $*${RESET}"; }
warn() { echo -e "${YELLOW}⚠️  $*${RESET}"; }
info() { echo -e "${BLUE}==> $*${RESET}"; }
title() { echo -e "${WHITE}🔷 $*${RESET}"; }
step() { echo -e "${CYAN}   → $*${RESET}"; }
hr() { echo -e "${WHITE}────────────────────────────────────────${RESET}"; }

# Configurações
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v13.0.enterprise-inventory.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v13.0.enterprise-inventory.json"

LOG_DIR="${SCRIPT_DIR}/logs"
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/execucao_$(date +%Y%m%d_%H%M%S).log"
APP_LOG="${LOG_DIR}/app.log"

log() { echo "[$(date +'%H:%M:%S')] $*" | tee -a "${LOG_FILE}"; }

check_files() {
    title "Verificando arquivos"
    [[ -f "${COLLECTION}" ]] || { err "Collection não encontrada"; exit 1; }
    [[ -f "${ENV_FILE}" ]] || { err "Environment não encontrado"; exit 1; }
    [[ -f "${PROJECT_ROOT}/mvnw" ]] || { err "mvnw não encontrado na raiz"; exit 1; }
    ok "Arquivos OK"
    hr
}

check_port() {
    title "Verificando porta 8080"
    if command -v netstat >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep -q ":8080.*LISTENING"; then
        warn "Porta 8080 em uso, liberando..."
        pids=$(netstat -ano | grep ":8080" | grep LISTENING | awk '{print $5}')
        for pid in $pids; do taskkill //PID $pid //F >/dev/null 2>&1 || true; done
        sleep 2
    fi
    ok "Porta 8080 OK"
    hr
}

reset_db() {
    title "Resetando banco"
    export PGPASSWORD=admin
    psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS db_multitenancy;" >/dev/null 2>&1
    psql -U postgres -d postgres -c "CREATE DATABASE db_multitenancy;" >/dev/null 2>&1
    ok "Banco resetado"
    hr
}

create_symlink() {
    title "Criando link para mvnw"
    ln -sf "${PROJECT_ROOT}/mvnw" "${SCRIPT_DIR}/mvnw" 2>/dev/null || true
    ok "Link criado"
    hr
}

start_app() {
    title "Iniciando aplicação"
    step "Comando: ${PROJECT_ROOT}/mvnw spring-boot:run"
    
    : > "${APP_LOG}"
    (cd "${PROJECT_ROOT}" && ./mvnw spring-boot:run) > "${APP_LOG}" 2>&1 &
    APP_PID=$!
    step "PID: ${APP_PID}"
    
    info "Aguardando aplicação (timeout: 180s)"
    local elapsed=0
    while [[ $elapsed -lt 180 ]]; do
        if grep -q "Started .*Application" "${APP_LOG}" 2>/dev/null; then
            ok "Aplicação iniciada em ${elapsed}s"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    
    err "Timeout! Últimas linhas:"
    tail -20 "${APP_LOG}"
    exit 1
}

health_check() {
    title "Health check"
    local url="http://localhost:8080/actuator/health"
    for i in {1..30}; do
        if curl -fsS "${url}" >/dev/null 2>&1; then
            ok "Health check OK"
            hr
            return 0
        fi
        sleep 2
    done
    err "Health check falhou"
    exit 1
}

run_tests() {
    title "Executando testes (ENTERPRISE + INVENTORY)"
    step "Collection: $(basename "${COLLECTION}")"
    
    # Criar environment temporário
    TEMP_ENV="${SCRIPT_DIR}/.env.temp.json"
    cp "${ENV_FILE}" "${TEMP_ENV}"
    
    if newman run "${COLLECTION}" -e "${TEMP_ENV}" --reporters cli,json --reporter-json-export "${SCRIPT_DIR}/.newman-report.json"; then
        ok "Testes concluídos com sucesso"
        rm -f "${TEMP_ENV}"
    else
        err "Testes falharam"
        rm -f "${TEMP_ENV}"
        return 1
    fi
    hr
}

cleanup() {
    title "Limpando"
    if [[ -n "${APP_PID:-}" ]]; then
        kill $APP_PID 2>/dev/null || true
        wait $APP_PID 2>/dev/null || true
    fi
    rm -f "${SCRIPT_DIR}"/.env.*.json "${SCRIPT_DIR}"/.newman-*.json "${SCRIPT_DIR}"/mvnw
    ok "Limpeza concluída"
    hr
}

main() {
    hr
    title "TESTE V13.0 - ENTERPRISE + INVENTORY (VERSÃO SIMPLES)"
    hr
    
    trap cleanup EXIT
    
    check_files
    check_port
    reset_db
    create_symlink
    start_app
    health_check
    run_tests
    
    hr
    ok "TESTE V13.0 FINALIZADO"
    hr
}

main "$@"