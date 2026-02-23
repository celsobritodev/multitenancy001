#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# multitenancy001 - E2E RESET + RUN (Postgres local)
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
import json, time, uuid, os

src = os.environ["ENV_FILE"]
out = os.environ["ENV_EFFECTIVE"]

with open(src, "r", encoding="utf-8") as f:
    d = json.load(f)

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

# Control Plane - MANTER VALORES DO ARQUIVO ORIGINAL
# Não fazer nada, apenas garantir que existam
controlplane_email = get("controlplane_email")
controlplane_password = get("controlplane_password")

# Se por algum motivo estiverem vazios, usar valores padrão (fallback seguro)
if not controlplane_email:
    setv("controlplane_email", "superadmin@platform.local")
    print("⚠️ controlplane_email não encontrado, usando fallback")
if not controlplane_password:
    setv("controlplane_password", "admin123")
    print("⚠️ controlplane_password não encontrado, usando fallback")

# Tokens vazios
for k in ["tenant_access_token","tenant_refresh_token","account_id","tenantSchema",
          "controlplane_access_token","controlplane_refresh_token","controlplane_refresh_token_old",
          "controlplane_refresh_token_new","controlplane_account_id"]:
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

# Output para debug
print("tenant_email =", get("tenant_email"))
print("tenant_password = ***")
print("tenant_tax_id_type =", get("tenant_tax_id_type"))
print("tenant_tax_id_number =", get("tenant_tax_id_number"))
print("controlplane_email =", get("controlplane_email"))
print("controlplane_password = ***")
print("effective_env_written =", out)
PY
# Adicione esta linha após o bloco Python, antes do newman run
log "==> Conteúdo do arquivo efetivo gerado:"
cat "${ENV_EFFECTIVE}" | grep -E "controlplane_(email|password)" || echo "Variáveis controlplane não encontradas!"


log "==> Run Newman"
newman run "${COLLECTION}" -e "${ENV_EFFECTIVE}" --bail

log "==> DONE ✅"