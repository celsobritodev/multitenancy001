#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

COLLECTION="${SCRIPT_DIR}/multitenancy001.postman_collection.v29.subscription-billing-binding.json"
ENV_FILE="${SCRIPT_DIR}/multitenancy001.local.postman_environment.v29.subscription-billing-binding.json"
TEMP_ENV="${SCRIPT_DIR}/.env.effective.json"
APP_LOG="${SCRIPT_DIR}/logs/.e2e-app.log"

APP_PORT="${APP_PORT:-8080}"
APP_START_TIMEOUT="${APP_START_TIMEOUT:-180}"

APP_PID=""

title() {
  echo "────────────────────────────────────────────────────"
  echo "🔷 $1"
  echo "────────────────────────────────────────────────────"
}

step() { echo "   → $1"; }
ok() { echo "✅ $1"; }
warn() { echo "⚠️ $1"; }
err() { echo "❌ $1"; }
hr() { echo "────────────────────────────────────────────────────"; }

cleanup_runner() {
  if [[ -n "${APP_PID:-}" ]]; then
    kill "${APP_PID}" >/dev/null 2>&1 || true
    wait "${APP_PID}" >/dev/null 2>&1 || true
  fi
  rm -f "${SCRIPT_DIR}/.app.pid" >/dev/null 2>&1 || true
  if [[ -L "${SCRIPT_DIR}/mvnw" ]]; then
    rm -f "${SCRIPT_DIR}/mvnw" >/dev/null 2>&1 || true
  fi
}
trap cleanup_runner EXIT

echo "────────────────────────────────────────────────────"
echo "🔷 TESTE V29 - SUBSCRIPTION / BILLING BINDING (ULTRA)"
echo "   Iniciando execução em: $(date)"
echo "   Path do script: ${SCRIPT_DIR}"
echo "────────────────────────────────────────────────────"

title "Verificando requisitos"
[[ -f "${COLLECTION}" ]] || { err "Collection não encontrada"; exit 1; }
[[ -f "${ENV_FILE}" ]] || { err "Environment não encontrado"; exit 1; }
[[ -f "${PROJECT_ROOT}/mvnw" ]] || { err "mvnw não encontrado na raiz do projeto"; exit 1; }
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

title "Criando link para mvnw"
ln -sf "${PROJECT_ROOT}/mvnw" "${SCRIPT_DIR}/mvnw" 2>/dev/null || true
ok "Link criado"
hr

title "Preparando environment"
cp "${ENV_FILE}" "${TEMP_ENV}"
ok "Environment pronto"
hr

title "Iniciando aplicação"
mkdir -p "${SCRIPT_DIR}/logs"
: > "${APP_LOG}"
(cd "${PROJECT_ROOT}" && ./mvnw spring-boot:run) > "${APP_LOG}" 2>&1 &
APP_PID=$!
echo "${APP_PID}" > "${SCRIPT_DIR}/.app.pid"
step "PID: ${APP_PID}"
echo "==> Aguardando aplicação (timeout: ${APP_START_TIMEOUT}s)"

for i in $(seq 1 "${APP_START_TIMEOUT}"); do
  if grep -Eq "Started .*Application|Started .* in .* seconds" "${APP_LOG}" 2>/dev/null; then
    ok "Aplicação iniciada em ${i}s"
    break
  fi
  if ! kill -0 "${APP_PID}" >/dev/null 2>&1; then
    err "Aplicação encerrou antes do boot completar"
    tail -50 "${APP_LOG}" || true
    exit 1
  fi
  sleep 1
  if [[ "${i}" -eq "${APP_START_TIMEOUT}" ]]; then
    err "Timeout aguardando aplicação"
    tail -50 "${APP_LOG}" || true
    exit 1
  fi
done

title "Health check"
curl -fsS "http://localhost:${APP_PORT}/actuator/health" >/dev/null
ok "Health check OK"
hr

for i in 1 2 3; do
  title "Executando ULTRA run #${i}"
  newman run "${COLLECTION}"     -e "${TEMP_ENV}"     --export-environment "${TEMP_ENV}"     --reporters cli,json     --reporter-json-export "${SCRIPT_DIR}/logs/newman-v29-ultra-run-${i}.json"
  ok "ULTRA run #${i} concluído"
  hr
done

ok "V29 ULTRA concluída com sucesso"
