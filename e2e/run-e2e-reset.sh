#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# multitenancy001 - E2E RESET + RUN (Postgres local)
# E2E RUNNER VERSION: v10.9.8

# Print runner version early (helps confirm you're using the right file)
echo "==> Runner: run-e2e-reset.sh (v10.9.8)"
# (tail app log on newman failure)
# =========================================================

# Always run from project root (relative paths are stable)
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# ---------------------------------------------------------
# Config (Postgres)
# ---------------------------------------------------------
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-admin}"
DB_NAME="${DB_NAME:-db_multitenancy}"

export PGHOST="${PGHOST:-$DB_HOST}"
export PGPORT="${PGPORT:-$DB_PORT}"
export PGUSER="${PGUSER:-$DB_USER}"
export PGPASSWORD="${PGPASSWORD:-$DB_PASSWORD}"

# ---------------------------------------------------------
# Paths (relative by default)
# ---------------------------------------------------------
COLLECTION="${COLLECTION:-e2e/multitenancy001.postman_collection.json}"
ENV_FILE="${ENV_FILE:-e2e/multitenancy001.postman_environment.json}"

APP_LOG=".e2e-app.log"
APP_PID=""
APP_PORT="${APP_PORT:-8080}"
ENV_EFFECTIVE=".env.effective.json"

cleanup() {
  if [[ -n "${APP_PID}" ]]; then
    echo "==> Stopping app (pid=${APP_PID})..."
    kill "${APP_PID}" >/dev/null 2>&1 || true
    wait "${APP_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

log() { echo "$*"; }

psql_auth_hint() {
  cat <<EOF
[HINT] psql pediu senha / falhou autenticação.
       Ajuste assim:
         DB_PASSWORD=SUA_SENHA ./e2e/run-e2e-reset.sh

       Config atual:
         DB_HOST=${DB_HOST}
         DB_PORT=${DB_PORT}
         DB_USER=${DB_USER}
         DB_NAME=${DB_NAME}
EOF
}

log "==> Drop DB (${DB_NAME})"

if [[ ! -f "${COLLECTION}" ]]; then
  log "ERROR: collection not found: ${COLLECTION}"
  ls -la e2e || true
  exit 2
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  log "ERROR: env not found: ${ENV_FILE}"
  ls -la e2e || true
  exit 2
fi

psql -v ON_ERROR_STOP=1 -d postgres <<SQL || { psql_auth_hint; exit 1; }
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB_NAME}';
DROP DATABASE IF EXISTS "${DB_NAME}";
CREATE DATABASE "${DB_NAME}";
SQL

log "==> Start app"
: > "${APP_LOG}"
( ./mvnw -DskipTests spring-boot:run > "${APP_LOG}" 2>&1 ) &
APP_PID="$!"
log "==> App started (pid=${APP_PID}). Logs: ${APP_LOG}"

log "==> Wait for STARTED"
until grep -qE "Started .*Application" "${APP_LOG}" 2>/dev/null; do sleep 1; done
log "==> App STARTED."

log "==> Health check"
until curl -fsS "http://localhost:${APP_PORT}/actuator/health" >/dev/null 2>&1; do sleep 1; done
log "==> Health OK."

log "==> Patch ENV (effective env for Newman)"
ENV_FILE="${ENV_FILE}" ENV_EFFECTIVE="${ENV_EFFECTIVE}" python - <<'PY'
import json, time, uuid, os, sys

# Configurar encoding para UTF-8 no Windows
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

src = os.environ["ENV_FILE"]
out = os.environ["ENV_EFFECTIVE"]

try:
    with open(src, "r", encoding="utf-8") as f:
        d = json.load(f)
except Exception as e:
    print(f"Erro ao ler arquivo: {e}")
    sys.exit(1)

vals = d.get("values", [])
by_key = {v.get("key"): v for v in vals if isinstance(v, dict) and "key" in v}

def get(key):
    v = by_key.get(key)
    return None if not v else v.get("value")

def setv(key, value):
    if key in by_key:
        by_key[key]["value"] = value
        by_key[key]["enabled"] = True
    else:
        by_key[key] = {"key": key, "value": value, "enabled": True}

# Base URL
if not get("base_url"):
    setv("base_url", "http://localhost:8080")

# Tenant email - só gera se não existir
tenant_email = get("tenant_email")
if not tenant_email or str(tenant_email).strip() == "":
    ts = int(time.time())
    generated = f"e2e_{ts}_{uuid.uuid4().hex[:6]}@tenant.local"
    setv("tenant_email", generated)

# Tenant password - só define se não existir
if not get("tenant_password"):
    setv("tenant_password", "admin123")

# Tax fields
if not get("tenant_tax_id_type"):
    setv("tenant_tax_id_type", "CNPJ")
if not get("tenant_tax_id_number"):
    setv("tenant_tax_id_number", "12345678000199")

# Control Plane
controlplane_email = get("controlplane_email")
controlplane_password = get("controlplane_password")

if not controlplane_email:
    setv("controlplane_email", "superadmin@platform.local")
    print("[WARN] controlplane_email não encontrado, usando fallback")
if not controlplane_password:
    setv("controlplane_password", "admin123")
    print("[WARN] controlplane_password não encontrado, usando fallback")

# Lista completa de tokens para a V5
token_keys = [
    "tenant_access_token", "tenant_refresh_token", "account_id", "tenantSchema",
    "superadmin_token", "superadmin_refresh", "superadmin_user_id",
    "billing_token", "billing_refresh", "billing_token_new", "billing_refresh_new",
    "support_token", "support_refresh", "support_token_new", "support_refresh_new",
    "operator_token", "operator_refresh", "operator_token_new", "operator_refresh_new",
    "controlplane_access_token", "controlplane_refresh_token", 
    "controlplane_refresh_token_old", "controlplane_refresh_token_new", 
    "controlplane_account_id"
]

for k in token_keys:
    if k not in by_key:
        setv(k, "")

# Preservar ordem original
original_keys = [v.get("key") for v in vals if isinstance(v, dict) and "key" in v]
new_vals, seen = [], set()
for k in original_keys:
    if k in by_key and k not in seen:
        new_vals.append(by_key[k]); seen.add(k)
for k, v in by_key.items():
    if k not in seen:
        new_vals.append(v); seen.add(k)

d["values"] = new_vals

with open(out, "w", encoding="utf-8") as f:
    json.dump(d, f, ensure_ascii=False, indent=2)

# Output simples sem caracteres especiais
print(f"tenant_email = {get('tenant_email')}")
print("tenant_password = ***")
print(f"tenant_tax_id_type = {get('tenant_tax_id_type')}")
print(f"tenant_tax_id_number = {get('tenant_tax_id_number')}")
print(f"controlplane_email = {get('controlplane_email')}")
print("controlplane_password = ***")
print(f"effective_env_written = {out}")
PY

log "==> Conteúdo do arquivo efetivo gerado:"
# Usar cat com tratamento para Windows
if [[ -f "${ENV_EFFECTIVE}" ]]; then
    grep -E "controlplane_(email|password)" "${ENV_EFFECTIVE}" 2>/dev/null || echo "Variáveis controlplane não encontradas!"
else
    echo "Arquivo ${ENV_EFFECTIVE} não encontrado!"
fi

log "==> Run Newman"
# Desabilitar cores no Newman para evitar problemas de encoding
if ! newman run "${COLLECTION}" -e "${ENV_EFFECTIVE}" --bail --color off; then
  echo
  echo "==========================================================="
  echo "❌ Newman FAILED. Showing last 200 lines of app log:"
  echo "   ${APP_LOG}"
  echo "==========================================================="
  if [[ -f "${APP_LOG}" ]]; then
    tail -n 200 "${APP_LOG}" || true
  else
    echo "(log file not found)"
  fi
  
  echo
  echo "==> Diagnostics: attempt to extract shared_email and check public.login_identities"
  if command -v grep >/dev/null 2>&1 && [[ -f "${APP_LOG}" ]]; then
    shared_email="$(grep -oE "shared_[0-9]+_[a-f0-9]+@tenant\.local" "${APP_LOG}" | tail -n 1 || true)"
    if [[ -n "${shared_email}" ]]; then
      echo "   shared_email detected: ${shared_email}"
      if command -v psql >/dev/null 2>&1; then
        echo "   Querying public.login_identities for shared_email..."
        psql -v ON_ERROR_STOP=0 -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -c \
"SELECT subject_type, account_id, subject_id, email FROM public.login_identities WHERE email = '${shared_email}' ORDER BY account_id;" || true
      else
        echo "   (psql not found in PATH, skipping DB check)"
      fi
    else
      echo "   (shared_email not found in app log; skipping DB check)"
    fi
  else
    echo "   (grep or app log not available; skipping DB check)"
  fi
echo "==========================================================="
  exit 1
fi

log "==> DONE ✅"




