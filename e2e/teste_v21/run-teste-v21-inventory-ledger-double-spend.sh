#!/usr/bin/env bash
set -Eeuo pipefail

clear

RESET='\033[0m'; GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; WHITE='\033[1;37m'; CYAN='\033[0;36m'

ok() { echo -e "${GREEN}✅ $*${RESET}"; }
err() { echo -e "${RED}❌ $*${RESET}"; }
warn() { echo -e "${YELLOW}⚠️  $*${RESET}"; }
info() { echo -e "${BLUE}==> $*${RESET}"; }
title() { echo -e "${WHITE}🔷 $*${RESET}"; }
step() { echo -e "${CYAN}   → $*${RESET}"; }
hr() { echo -e "${WHITE}────────────────────────────────────────${RESET}"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v21.0.inventory-ledger-double-spend-reconciliation.strict.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v21.0.inventory-ledger-double-spend-reconciliation.strict.json"
LOG_DIR="${SCRIPT_DIR}/logs"
mkdir -p "${LOG_DIR}"
APP_LOG="${LOG_DIR}/app.log"

cleanup() {
  title "Limpando"
  [[ -n "${APP_PID:-}" ]] && kill "${APP_PID}" >/dev/null 2>&1 || true
  [[ -n "${APP_PID:-}" ]] && wait "${APP_PID}" >/dev/null 2>&1 || true
  rm -f "${SCRIPT_DIR}/.env.effective.json" "${SCRIPT_DIR}/.newman-report.json" 2>/dev/null || true
  [[ -L "${SCRIPT_DIR}/mvnw" ]] && rm -f "${SCRIPT_DIR}/mvnw"
  ok "Limpeza concluída"
  hr
}
trap cleanup EXIT

echo "────────────────────────────────────────"
echo "🔷 TESTE V21.0 - INVENTORY LEDGER / DOUBLE-SPEND RECONCILIATION (STRICT SUITE)"
echo "   Path do script: $SCRIPT_DIR"
echo "────────────────────────────────────────"

title "Verificando arquivos"
[[ -f "${COLLECTION}" ]] || { err "Collection não encontrada"; exit 1; }
[[ -f "${ENV_FILE}" ]] || { err "Environment não encontrado"; exit 1; }
[[ -f "${PROJECT_ROOT}/mvnw" ]] || { err "mvnw não encontrado na raiz"; exit 1; }
ok "Arquivos OK"
hr

title "Verificando porta 8080"
if command -v netstat >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep -q ":8080.*LISTENING"; then
  warn "Porta 8080 em uso, liberando..."
  pids=$(netstat -ano | grep ":8080" | grep LISTENING | awk '{print $5}')
  for pid in $pids; do taskkill //PID $pid //F >/dev/null 2>&1 || true; done
  sleep 2
fi
ok "Porta 8080 OK"
hr

title "Resetando banco"
export PGPASSWORD="${PGPASSWORD:-admin}"
psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'db_multitenancy' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS db_multitenancy;" >/dev/null
psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE db_multitenancy;" >/dev/null
ok "Banco resetado"
hr

title "Criando link para mvnw"
ln -sf "${PROJECT_ROOT}/mvnw" "${SCRIPT_DIR}/mvnw" 2>/dev/null || true
ok "Link criado"
hr

title "Preparando environment"
cp "${ENV_FILE}" "${SCRIPT_DIR}/.env.effective.json"
ok "Environment pronto"
hr

title "Iniciando aplicação"
step "Comando: ${PROJECT_ROOT}/mvnw spring-boot:run"
: > "${APP_LOG}"
(cd "${PROJECT_ROOT}" && ./mvnw spring-boot:run) > "${APP_LOG}" 2>&1 &
APP_PID=$!
step "PID: $APP_PID"
info "Aguardando aplicação (timeout: 180s)"
for i in $(seq 1 180); do
  if grep -Eq "Started .*Application|Started .* in .* seconds" "${APP_LOG}" 2>/dev/null; then
    ok "Aplicação iniciada em ${i}s"
    break
  fi
  sleep 1
  [[ $i -eq 180 ]] && { err "Timeout aguardando aplicação"; tail -50 "${APP_LOG}" || true; exit 1; }
done

title "Health check"
curl -fsS http://localhost:8080/actuator/health >/dev/null
ok "Health check OK"
hr

title "Executando testes (INVENTORY LEDGER / DOUBLE-SPEND RECONCILIATION STRICT)"
step "Collection: $(basename "${COLLECTION}")"
newman run "${COLLECTION}" -e "${SCRIPT_DIR}/.env.effective.json" --export-environment "${SCRIPT_DIR}/.env.effective.json" --reporters cli,json --reporter-json-export "${SCRIPT_DIR}/.newman-report.json"

ok "TESTE V20.0 FINALIZADO"
hr
