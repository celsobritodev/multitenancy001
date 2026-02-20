#!/usr/bin/env bash
set -euo pipefail

# ==========================================================
# CONFIG (já alinhado com seu application.properties)
# ==========================================================
BASE_URL="${BASE_URL:-http://localhost:8080}"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_ADMIN_USER="${DB_ADMIN_USER:-postgres}"
DB_ADMIN_DB="${DB_ADMIN_DB:-postgres}"     # banco admin para dropar o target
DB_NAME="${DB_NAME:-db_multitenancy}"
DB_PASS="${DB_PASS:-admin}"                # do seu application.properties

# Como subir o app (na raiz do projeto)
APP_START_CMD="${APP_START_CMD:-./mvnw -DskipTests spring-boot:run}"

# Newman (ajuste se você renomear versões)
COLLECTION="${COLLECTION:-e2e/multitenancy001.postman_collection.v12.9-categories-subcategories-negative.json}"
ENV_FILE="${ENV_FILE:-e2e/multitenancy001.local.postman_environment.v12.8.json}"

# ==========================================================
# INTERNAL
# ==========================================================
APP_PID_FILE=".e2e-app.pid"
APP_LOG_FILE=".e2e-app.log"

export PGPASSWORD="${DB_PASS}"

cleanup() {
  # Para o app se estiver rodando via PID file
  if [[ -f "$APP_PID_FILE" ]]; then
    local pid
    pid="$(cat "$APP_PID_FILE" || true)"
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "==> Stopping app (pid=$pid)..."
      kill "$pid" 2>/dev/null || true

      for _ in {1..30}; do
        if kill -0 "$pid" 2>/dev/null; then
          sleep 1
        else
          break
        fi
      done

      if kill -0 "$pid" 2>/dev/null; then
        echo "==> App did not stop gracefully; killing (pid=$pid)..."
        kill -9 "$pid" 2>/dev/null || true
      fi
    fi
    rm -f "$APP_PID_FILE"
  fi
}

# ✅ Trap com fallback automático do log quando houver erro
trap '{
  status=$?
  if [[ $status -ne 0 ]]; then
    echo "==> ERROR detected (exit code=$status). Showing last 200 app log lines:"
    tail -n 200 "$APP_LOG_FILE" || true
  fi
  cleanup
  exit $status
}' EXIT

stop_app_by_port_8080() {
  # Fallback: mata o processo que está ouvindo em 8080 (Windows)
  if command -v netstat >/dev/null 2>&1 && command -v taskkill >/dev/null 2>&1; then
    local pid
    pid="$(netstat -ano | grep -E "LISTENING[[:space:]]+8080" | awk '{print $NF}' | head -n 1 || true)"
    if [[ -n "${pid:-}" ]]; then
      echo "==> Found process listening on 8080 (pid=$pid). Stopping..."
      taskkill //PID "$pid" //F >/dev/null 2>&1 || true
    fi
  fi
}

stop_app_if_running() {
  # 1) se tiver PID file, para por ele
  if [[ -f "$APP_PID_FILE" ]]; then
    cleanup
    return 0
  fi

  # 2) senão, tenta por porta
  stop_app_by_port_8080
}

drop_and_recreate_db() {
  echo "==> Dropping database '${DB_NAME}' and recreating..."
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_ADMIN_USER" -d "$DB_ADMIN_DB" -v ON_ERROR_STOP=1 <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS ${DB_NAME};
CREATE DATABASE ${DB_NAME};
SQL
  echo "==> DB recreated: ${DB_NAME}"
}

start_app() {
  echo "==> Starting app: $APP_START_CMD"
  rm -f "$APP_LOG_FILE"

  # Sobe em background e salva pid
  bash -lc "$APP_START_CMD" >"$APP_LOG_FILE" 2>&1 &
  local pid=$!
  echo "$pid" > "$APP_PID_FILE"
  echo "==> App started (pid=$pid). Logs: $APP_LOG_FILE"
}

# ✅ Novo: espera o log "Started ...Application"
wait_for_started_log() {
  echo "==> Waiting for Spring Boot 'Started ...Application' log line..."
  for i in {1..240}; do
    if grep -E "Started .*Application" "$APP_LOG_FILE" >/dev/null 2>&1; then
      echo "==> App log indicates STARTED."
      return 0
    fi
    sleep 1
  done

  echo "==> App did not print STARTED line in time."
  echo "==> Last 200 log lines:"
  tail -n 200 "$APP_LOG_FILE" || true
  return 1
}

wait_for_health() {
  echo "==> Waiting for health at $BASE_URL/actuator/health ..."
  for i in {1..180}; do
    if curl -fsS "$BASE_URL/actuator/health" >/dev/null 2>&1; then
      echo "==> Health OK."
      return 0
    fi
    sleep 1
  done

  echo "==> Health did not become ready. Last logs:"
  tail -n 200 "$APP_LOG_FILE" || true
  return 1
}

run_newman() {
  echo "==> Running Newman..."
  newman run "$COLLECTION" -e "$ENV_FILE" --bail
  echo "==> Newman OK."
}

# ==========================================================
# MAIN
# ==========================================================
echo "==> E2E RESET + RUN (Postgres local)"

stop_app_if_running
drop_and_recreate_db
start_app

# ✅ Espera real de boot do Spring antes do Newman
wait_for_started_log

# ✅ Confere health HTTP
wait_for_health

# ✅ Warm-up anti-race (evita 500 na primeira request de negócio)
echo "==> Warm-up (2s) ..."
sleep 2

run_newman

echo "==> DONE ✅"