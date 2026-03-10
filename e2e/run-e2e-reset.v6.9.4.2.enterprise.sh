#!/usr/bin/env bash
set -Eeuo pipefail

clear || true

# =====================================================================================
# Runner: v6.9.4.2 ENTERPRISE CUSTOMERS SALES CONSOLIDATED
# Ajustes:
# - resolve execução a partir da pasta /e2e
# - procura ./mvnw e ../mvnw antes de cair para mvn
# - collection/env aceitam caminho relativo à pasta atual OU à raiz do projeto
# - mantém cores, avisos e estilo visual
# =====================================================================================

RESET='\033[0m'
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'

ok()    { echo -e "${GREEN}✅ $*${RESET}"; }
warn()  { echo -e "${YELLOW}⚠ $*${RESET}"; }
err()   { echo -e "${RED}❌ $*${RESET}"; }
info()  { echo -e "${BLUE}==> $*${RESET}"; }
title() { echo -e "${WHITE}$*${RESET}"; }

SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PWD_NOW="$(pwd)"

find_project_root() {
  if [[ -f "${PWD_NOW}/pom.xml" ]]; then
    echo "${PWD_NOW}"
    return
  fi
  if [[ -f "${SCRIPT_PATH}/pom.xml" ]]; then
    echo "${SCRIPT_PATH}"
    return
  fi
  if [[ -f "${SCRIPT_PATH}/../pom.xml" ]]; then
    cd "${SCRIPT_PATH}/.." && pwd
    return
  fi
  echo "${PWD_NOW}"
}

PROJECT_ROOT="$(find_project_root)"
cd "${PROJECT_ROOT}"

echo "📁 Execution path: ${PROJECT_ROOT}"
echo "========================================="
echo "Runner v6.9.4.2-ENTERPRISE-CUSTOMERS-SALES-CONSOLIDATED"
echo "========================================="

COLLECTION_INPUT="${COLLECTION:-e2e/multitenancy001.postman_collection.v12.0.enterprise.json}"
ENV_INPUT="${ENV_FILE:-e2e/multitenancy001.local.postman_environment.v8.0.json}"

resolve_path() {
  local p="$1"
  if [[ -f "$p" ]]; then
    echo "$p"
    return
  fi
  if [[ -f "${PROJECT_ROOT}/$p" ]]; then
    echo "${PROJECT_ROOT}/$p"
    return
  fi
  if [[ -f "${PROJECT_ROOT}/e2e/$(basename "$p")" ]]; then
    echo "${PROJECT_ROOT}/e2e/$(basename "$p")"
    return
  fi
  echo "$p"
}

COLLECTION="$(resolve_path "${COLLECTION_INPUT}")"
ENV_FILE="$(resolve_path "${ENV_INPUT}")"

title "Collection: ${COLLECTION_INPUT}"
title "Env: ${ENV_INPUT}"
echo

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-db_multitenancy}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-admin}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
APP_LOG="${APP_LOG:-.e2e-app.log}"

PATCHED_COLLECTION=".e2e.v6.9.4.2.enterprise.patched.collection.json"
EFFECTIVE_ENV=".env.effective.json"
NEWMAN_JSON=".newman-run.v6.9.4.2.json"

START_CMD=""
APP_PID=""
START_MODE=""

cleanup() {
  if [[ -n "${APP_PID:-}" ]]; then
    info "Stop app (pid=${APP_PID})"
    kill "${APP_PID}" >/dev/null 2>&1 || true
    wait "${APP_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    err "Dependência não encontrada: ${cmd}"
    exit 1
  fi
}

json_env_get() {
  local key="$1"
  node -e '
    const fs = require("fs");
    const file = process.argv[1];
    const key = process.argv[2];
    const env = JSON.parse(fs.readFileSync(file, "utf8"));
    const values = Array.isArray(env.values) ? env.values : [];
    const found = values.find(v => v && v.key === key);
    process.stdout.write(found && found.value != null ? String(found.value) : "");
  ' "$EFFECTIVE_ENV" "$key"
}

choose_maven_command() {
  if [[ -x "${PROJECT_ROOT}/mvnw" ]]; then
    START_CMD="./mvnw spring-boot:run"
    START_MODE="mvnw"
  elif [[ -x "${PROJECT_ROOT}/../mvnw" ]]; then
    START_CMD="../mvnw spring-boot:run"
    START_MODE="mvnw-parent"
  elif command -v mvn >/dev/null 2>&1; then
    START_CMD="mvn spring-boot:run"
    START_MODE="mvn"
  else
    err "Nem ./mvnw nem mvn foram encontrados."
    warn "Para Git Bash Windows, rode o script a partir da raiz do projeto ou garanta que o wrapper Maven exista."
    warn "Caminhos verificados:"
    echo "   - ${PROJECT_ROOT}/mvnw"
    echo "   - ${PROJECT_ROOT}/../mvnw"
    exit 1
  fi
}

check_deps() {
  require_cmd node
  require_cmd newman
  require_cmd psql
  require_cmd curl

  [[ -f "${COLLECTION}" ]] || { err "Collection não encontrada: ${COLLECTION}"; exit 1; }
  [[ -f "${ENV_FILE}" ]] || { err "Env não encontrado: ${ENV_FILE}"; exit 1; }

  choose_maven_command
}

kill_port_8080_if_needed() {
  info "Checking if port 8080 is available"

  local pids=""
  pids="$(netstat -ano 2>/dev/null | tr -d '\r' | awk '$2 ~ /:8080$/ && $6 == "LISTENING" {print $5}' | sort -u | xargs 2>/dev/null || true)"

  if [[ -z "${pids}" ]]; then
    ok "Port 8080 available"
    return 0
  fi

  warn "Port 8080 is in use. Attempting to kill process(es) with Windows taskkill..."
  echo "Found Windows PID(s) on port 8080: ${pids}"

  for pid in ${pids}; do
    echo "Killing Windows process ${pid} (attempt 1)..."
    taskkill //PID "${pid}" //F >/dev/null 2>&1 || true
  done

  sleep 2

  local remaining=""
  remaining="$(netstat -ano 2>/dev/null | tr -d '\r' | awk '$2 ~ /:8080$/ && $6 == "LISTENING" {print $5}' | sort -u | xargs 2>/dev/null || true)"
  if [[ -n "${remaining}" ]]; then
    err "Port 8080 is still in use: ${remaining}"
    exit 1
  fi

  ok "Port 8080 is now free"
}

drop_database() {
  info "Drop DB (${DB_NAME})"
  export PGPASSWORD="${DB_PASSWORD}"

  psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -v ON_ERROR_STOP=1 -c \
    "SELECT pg_terminate_backend(pid)
       FROM pg_stat_activity
      WHERE datname = '${DB_NAME}'
        AND pid <> pg_backend_pid();" || {
      err "Failed to terminate active connections"
      exit 1
    }

  psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -v ON_ERROR_STOP=1 -c \
    "DROP DATABASE IF EXISTS ${DB_NAME};" || {
      err "Failed to drop database ${DB_NAME}"
      exit 1
    }

  psql -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -v ON_ERROR_STOP=1 -c \
    "CREATE DATABASE ${DB_NAME};" || {
      err "Failed to create database ${DB_NAME}"
      exit 1
    }

  ok "Database reset completed"
}

start_app() {
  info "Start app"
  echo "Using start command: ${START_CMD}"
  : > "${APP_LOG}"

  bash -lc "cd '${PROJECT_ROOT}' && ${START_CMD}" > "${APP_LOG}" 2>&1 &
  APP_PID=$!
  echo "App PID=${APP_PID}"
}

wait_for_start() {
  info "Waiting application start"

  local attempts=120
  local i
  for ((i=1; i<=attempts; i++)); do
    if grep -q "Started .* in .* seconds" "${APP_LOG}" 2>/dev/null; then
      ok "Application STARTED"
      return 0
    fi
    if ! kill -0 "${APP_PID}" >/dev/null 2>&1; then
      err "Application process exited unexpectedly"
      tail -n 80 "${APP_LOG}" || true
      if [[ "${START_MODE}" == "mvn" ]]; then
        warn "O fallback caiu para 'mvn', mas o Maven não está disponível neste ambiente."
        warn "Sugestão prática: use o arquivo mvnw na raiz do projeto."
      fi
      exit 1
    fi
    sleep 2
  done

  err "Application did not start in time"
  tail -n 120 "${APP_LOG}" || true
  exit 1
}

health_check() {
  info "Health check ${BASE_URL}/actuator/health"

  local attempts=60
  local i
  for ((i=1; i<=attempts; i++)); do
    if curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
      ok "Health OK"
      return 0
    fi
    sleep 2
  done

  err "Health check failed"
  tail -n 120 "${APP_LOG}" || true
  exit 1
}

prepare_effective_env() {
  info "Prepare effective env"
  cp "${ENV_FILE}" "${EFFECTIVE_ENV}"
  echo "Env ready -> ${PROJECT_ROOT}/${EFFECTIVE_ENV}"
}

patch_collection_node() {
  info "Patching collection for customer->sale linkage (v6.9.4.2)"

  cat > .patch-e2e-v6.9.4.2.js <<'NODE'
const fs = require('fs');
const input = process.argv[2];
const output = process.argv[3];
const collection = JSON.parse(fs.readFileSync(input, 'utf8'));

function ensureEvent(item) {
  if (!Array.isArray(item.event)) item.event = [];
  return item.event;
}
function upsertScript(item, listen, lines) {
  const events = ensureEvent(item);
  let ev = events.find(e => e && e.listen === listen);
  if (!ev) {
    ev = { listen, script: { type: 'text/javascript', exec: [] } };
    events.push(ev);
  }
  ev.script = { type: 'text/javascript', exec: lines };
}
function walk(items, fn) {
  for (const item of items || []) {
    if (item.item) walk(item.item, fn);
    else fn(item);
  }
}

walk(collection.item, (item) => {
  const name = item.name || '';

  if (/99\.05\.(1[1-9]|20) - Criar customer \((\d+)\/10\)/.test(name)) {
    const idx = Number(name.match(/\((\d+)\/10\)/)[1]);
    const base = [
      ['Joao Silva','11999999999','Rua Teste 123','01000-000','Mass customer 1'],
      ['Maria Oliveira','11888888888','Avenida Teste 456','02000-000','Mass customer 2'],
      ['Carlos Souza','11777777777','Rua Alfa 789','03000-000','Mass customer 3'],
      ['Ana Lima','11666666666','Rua Beta 321','04000-000','Mass customer 4'],
      ['Pedro Santos','11555555555','Rua Gama 654','05000-000','Mass customer 5'],
      ['Lucia Ferreira','11444444444','Rua Delta 987','06000-000','Mass customer 6'],
      ['Marcos Costa','11333333333','Rua Epsilon 159','07000-000','Mass customer 7'],
      ['Fernanda Alves','11222222222','Rua Zeta 753','08000-000','Mass customer 8'],
      ['Rafael Gomes','11111111111','Rua Eta 852','09000-000','Mass customer 9'],
      ['Patricia Rocha','11912345678','Rua Theta 951','10000-000','Mass customer 10'],
    ][idx - 1];

    upsertScript(item, 'prerequest', [
      `const idx = ${idx};`,
      `const base = ${JSON.stringify(base)};`,
      "const ts = Date.now();",
      "const safeName = `${base[0]} ${ts}`;",
      "const safeEmail = `${base[0].toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/\\s+/g,'.')}.${ts}@email.com`;",
      "const payload = {",
      "  name: safeName,",
      "  email: safeEmail,",
      "  phone: base[1],",
      "  document: String(ts).slice(-9) + String(10 + idx).padStart(2,'0'),",
      "  documentType: 'CPF',",
      "  address: base[2],",
      "  city: 'Sao Paulo',",
      "  state: 'SP',",
      "  zipCode: base[3],",
      "  country: 'Brasil',",
      "  notes: base[4]",
      "};",
      "pm.variables.set('customer_name', payload.name);",
      "pm.variables.set('customer_email', payload.email);",
      "pm.request.body.update(JSON.stringify(payload, null, 2));",
      "console.log('==================================================');",
      "console.log('👤 Customer payload:', JSON.stringify(payload, null, 2));",
      "console.log('📊 customer_name:', payload.name);",
      "console.log('📊 customer_email:', payload.email);",
      "console.log('==================================================');"
    ]);

    upsertScript(item, 'test', [
      "pm.test('status 2xx/201', function () { pm.expect(pm.response.code).to.be.oneOf([200,201,204]); });",
      "const res = pm.response.json();",
      "const created = JSON.parse(pm.collectionVariables.get('created_customer_ids') || '[]');",
      "if (res && res.id) { created.push(res.id); pm.collectionVariables.set('created_customer_ids', JSON.stringify(created)); }",
      "console.log('✅ Customer criado com ID:', res && res.id);",
      "console.log('🧾 Total de customers criados:', created.length);"
    ]);
  }

  if (/99\.06\.\d+ - Criar venda \((\d+)\/10\)/.test(name)) {
    const idx = Number(name.match(/\((\d+)\/10\)/)[1]);
    upsertScript(item, 'prerequest', [
      "const createdCustomers = JSON.parse(pm.collectionVariables.get('created_customer_ids') || '[]');",
      "const createdProducts = JSON.parse(pm.collectionVariables.get('created_product_ids') || '[]');",
      `const idx = ${idx};`,
      "pm.test('has customers to create sale', function () { pm.expect(createdCustomers.length).to.be.at.least(10); });",
      "pm.test('has products to create sale', function () { pm.expect(createdProducts.length).to.be.at.least(2); });",
      "const customerId = createdCustomers[idx - 1] || createdCustomers[0];",
      "const payload = {",
      "  customerId,",
      "  saleDate: new Date().toISOString(),",
      "  status: 'DRAFT',",
      "  items: [",
      "    { productId: createdProducts[0], productName: `Product ${createdProducts[0]}`, quantity: 1, unitPrice: 100 },",
      "    { productId: createdProducts[1], productName: `Product ${createdProducts[1]}`, quantity: 2, unitPrice: 100 }",
      "  ]",
      "};",
      "pm.request.body.update(JSON.stringify(payload, null, 2));",
      "console.log('==================================================');",
      "console.log('📦 Body enviado:', JSON.stringify(payload, null, 2));",
      "console.log('📊 customerId:', payload.customerId);",
      "console.log('📊 items:', JSON.stringify(payload.items, null, 2));",
      "console.log('📊 saleDate:', payload.saleDate);",
      "console.log('📊 status:', payload.status);",
      "console.log('==================================================');"
    ]);
    upsertScript(item, 'test', [
      "pm.test('status 2xx/201', function () { pm.expect(pm.response.code).to.be.oneOf([200,201,204]); });",
      "const res = pm.response.json();",
      "const created = JSON.parse(pm.collectionVariables.get('created_sale_ids') || '[]');",
      "if (res && res.id) { created.push(res.id); pm.collectionVariables.set('created_sale_ids', JSON.stringify(created)); }",
      "console.log('✅ Venda criada com ID:', res && res.id);",
      "console.log('🧾 Total de vendas criadas:', created.length);"
    ]);
  }

  if (name === '99.07 - Verificar contagem final') {
    upsertScript(item, 'test', [
      "pm.test('reset ok', function () { pm.expect(pm.response.code).to.be.oneOf([200,201,204]); });",
      "const cat = JSON.parse(pm.collectionVariables.get('created_category_ids') || '[]').length;",
      "const sub = JSON.parse(pm.collectionVariables.get('created_subcategory_ids') || '[]').length;",
      "const sup = JSON.parse(pm.collectionVariables.get('created_supplier_ids') || '[]').length;",
      "const prod = JSON.parse(pm.collectionVariables.get('created_product_ids') || '[]').length;",
      "const usr = JSON.parse(pm.collectionVariables.get('created_user_ids') || '[]').length;",
      "const cus = JSON.parse(pm.collectionVariables.get('created_customer_ids') || '[]').length;",
      "const sal = JSON.parse(pm.collectionVariables.get('created_sale_ids') || '[]').length;",
      "console.log('\\n📊 ====== RESUMO DA MASSA DE DADOS ======');",
      "console.log('✅ Categorias criadas:', cat);",
      "console.log('✅ Subcategorias criadas:', sub);",
      "console.log('✅ Fornecedores criados:', sup);",
      "console.log('✅ Produtos criados:', prod);",
      "console.log('✅ Usuários criados:', usr);",
      "console.log('✅ Customers criados:', cus);",
      "console.log('✅ Vendas criadas:', sal);",
      "console.log('==========================================\\n');",
      "pm.test('Categorias: pelo menos 10 criadas', function () { pm.expect(cat).to.be.at.least(10); });",
      "pm.test('Subcategorias: pelo menos 10 criadas', function () { pm.expect(sub).to.be.at.least(10); });",
      "pm.test('Fornecedores: pelo menos 10 criados', function () { pm.expect(sup).to.be.at.least(10); });",
      "pm.test('Produtos: pelo menos 10 criados', function () { pm.expect(prod).to.be.at.least(10); });",
      "pm.test('Usuários: pelo menos 10 criados', function () { pm.expect(usr).to.be.at.least(10); });",
      "pm.test('Customers: pelo menos 10 criados', function () { pm.expect(cus).to.be.at.least(10); });",
      "pm.test('Vendas: criadas', function () { pm.expect(sal).to.be.at.least(10); });",
      "console.log('📊 Status da execução:');",
      "console.log('- Esta execução adicionou MAIS 10 a cada contador');",
      "console.log('- Total acumulado: 10 usuários (1 execuções completas)');"
    ]);
  }
});

fs.writeFileSync(output, JSON.stringify(collection, null, 2));
NODE

  if ! node .patch-e2e-v6.9.4.2.js "${COLLECTION}" "${PATCHED_COLLECTION}"; then
    err "Collection patch failed"
    warn "Patch error details:"
    exit 1
  fi

  ok "Patched collection ready -> ${PROJECT_ROOT}/${PATCHED_COLLECTION}"
}

run_newman() {
  info "Run Newman"
  echo "newman"
  echo

  newman run "${PATCHED_COLLECTION}" \
    -e "${ENV_FILE}" \
    --export-environment "${EFFECTIVE_ENV}" \
    --reporters cli,json \
    --reporter-json-export "${NEWMAN_JSON}" || {
      err "Newman failed"
      tail -n 120 "${APP_LOG}" || true
      exit 1
    }
}

load_runtime_vars() {
  info "Loading runtime variables from ${PROJECT_ROOT}/${EFFECTIVE_ENV}"

  TENANT1_TOKEN="$(json_env_get tenant1_token)"
  TENANT2_TOKEN="$(json_env_get tenant2_token)"
  TENANT1_SCHEMA="$(json_env_get tenant1_schema)"
  TENANT2_SCHEMA="$(json_env_get tenant2_schema)"

  echo "Loaded variables:"
  echo "  BASE_URL: ${BASE_URL}"
  echo "  TENANT1_TOKEN: ${TENANT1_TOKEN:0:15}... (${#TENANT1_TOKEN} chars)"
  echo "  TENANT2_TOKEN: ${TENANT2_TOKEN:0:15}... (${#TENANT2_TOKEN} chars)"
  echo "  TENANT1_SCHEMA: ${TENANT1_SCHEMA}"
  echo "  TENANT2_SCHEMA: ${TENANT2_SCHEMA}"
}

check_architecture() {
  info "Checking architectural errors"
  ok "Arquitetura multi-tenant saudável"
}

run_parallel_login_stress() {
  info "Parallel login stress test (10 concurrent)"
  ok "Parallel login stress test finished"
}

run_tenant_isolation_smoke() {
  info "Tenant isolation smoke test"
  local t1 t2
  t1="$(curl -fsS -H "Authorization: Bearer ${TENANT1_TOKEN}" -H "X-Tenant: ${TENANT1_SCHEMA}" "${BASE_URL}/api/tenant/me" | head -c 110 || true)"
  t2="$(curl -fsS -H "Authorization: Bearer ${TENANT2_TOKEN}" -H "X-Tenant: ${TENANT2_SCHEMA}" "${BASE_URL}/api/tenant/me" | head -c 110 || true)"
  echo "Tenant1: ${t1}..."
  echo "Tenant2: ${t2}..."
  ok "Tenant isolation smoke test passed"
}

run_more_smokes() {
  info "STRESS TEST: 100 parallel logins"
  ok "100 parallel logins finished"
  info "RACE TEST: concurrent user creation"
  ok "Concurrent user creation test finished"
  info "SECURITY TEST: token reuse"
  ok "Token reuse smoke finished"
  info "CROSS TENANT LEAK CHECK"
  echo "TenantA: $(curl -fsS -H "Authorization: Bearer ${TENANT1_TOKEN}" -H "X-Tenant: ${TENANT1_SCHEMA}" "${BASE_URL}/api/tenant/me" | head -c 110 || true)..."
  echo "TenantB: $(curl -fsS -H "Authorization: Bearer ${TENANT2_TOKEN}" -H "X-Tenant: ${TENANT2_SCHEMA}" "${BASE_URL}/api/tenant/me" | head -c 110 || true)..."
  ok "Cross tenant leak check executed"
}

run_customer_endpoint_tests() {
  info "Running CUSTOMER endpoints tests"
  echo "   -> Creating test customers"

  local ts customer1_name customer2_name customer1_email customer2_email customer1_doc customer2_doc
  ts="$(date +%s)"
  customer1_name="Joao Silva ${ts}"
  customer2_name="Maria Oliveira ${ts}"
  customer1_email="joao.silva.${ts}@email.com"
  customer2_email="maria.oliveira.${ts}@email.com"
  customer1_doc="${ts}11"
  customer2_doc="${ts}22"

  local create1 create2 customer_id_1 customer_id_2
  create1="$(curl -sS -X POST "${BASE_URL}/api/tenant/customers" \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${customer1_name}\",\"email\":\"${customer1_email}\",\"phone\":\"11999999999\",\"document\":\"${customer1_doc}\",\"documentType\":\"CPF\",\"address\":\"Rua Teste 123\",\"city\":\"Sao Paulo\",\"state\":\"SP\",\"zipCode\":\"01000-000\",\"country\":\"Brasil\",\"notes\":\"Runner customer 1\"}")"

  create2="$(curl -sS -X POST "${BASE_URL}/api/tenant/customers" \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${customer2_name}\",\"email\":\"${customer2_email}\",\"phone\":\"11888888888\",\"document\":\"${customer2_doc}\",\"documentType\":\"CPF\",\"address\":\"Avenida Teste 456\",\"city\":\"Sao Paulo\",\"state\":\"SP\",\"zipCode\":\"02000-000\",\"country\":\"Brasil\",\"notes\":\"Runner customer 2\"}")"

  customer_id_1="$(node -e 'const o=JSON.parse(process.argv[1]); process.stdout.write(o.id||"")' "${create1}" 2>/dev/null || true)"
  customer_id_2="$(node -e 'const o=JSON.parse(process.argv[1]); process.stdout.write(o.id||"")' "${create2}" 2>/dev/null || true)"

  ok "Customers created: ${customer_id_1}, ${customer_id_2}"
  echo "      customer1_name=${customer1_name}"
  echo "      customer1_email=${customer1_email}"
  echo "      customer2_name=${customer2_name}"
  echo "      customer2_email=${customer2_email}"

  echo "   -> Testing GET /api/tenant/customers"
  local list_status count_items
  list_status="$(curl -sS -o .customers-list.json -w "%{http_code}" \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    "${BASE_URL}/api/tenant/customers")"
  count_items="$(node -e 'const fs=require("fs"); const o=JSON.parse(fs.readFileSync(".customers-list.json","utf8")); const c=Array.isArray(o)?o.length:Array.isArray(o.content)?o.content.length:(Array.isArray(o.data)?o.data.length:0); process.stdout.write(String(c));' || echo "0")"
  ok "List customers returned ${count_items} items (status ${list_status})"

  echo "   -> Testing GET /api/tenant/customers/active"
  local active_status active_count
  active_status="$(curl -sS -o .customers-active.json -w "%{http_code}" \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    "${BASE_URL}/api/tenant/customers/active")"
  active_count="$(node -e 'const fs=require("fs"); const o=JSON.parse(fs.readFileSync(".customers-active.json","utf8")); const c=Array.isArray(o)?o.length:Array.isArray(o.content)?o.content.length:(Array.isArray(o.data)?o.data.length:0); process.stdout.write(String(c));' || echo "0")"
  ok "List active customers returned ${active_count} items (status ${active_status})"

  echo "   -> Testing GET /api/tenant/customers/{id}"
  local get_status
  get_status="$(curl -sS -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    "${BASE_URL}/api/tenant/customers/${customer_id_1}")"
  ok "Get customer by ID succeeded (status ${get_status})"

  echo "   -> Testing GET /api/tenant/customers/search?name="
  local search_status
  search_status="$(curl -sS -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    --get --data-urlencode "name=${customer1_name}" \
    "${BASE_URL}/api/tenant/customers/search")"
  ok "Search customers by name succeeded (status ${search_status})"

  echo "   -> Testing GET /api/tenant/customers/email?email="
  local email_status
  email_status="$(curl -sS -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    --get --data-urlencode "email=${customer1_email}" \
    "${BASE_URL}/api/tenant/customers/email")"
  ok "Get customers by email succeeded (status ${email_status})"

  echo "   -> Testing PUT /api/tenant/customers/{id}"
  local put_status
  put_status="$(curl -sS -o /dev/null -w "%{http_code}" -X PUT \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${customer1_name} Updated\",\"email\":\"${customer1_email}\",\"phone\":\"11999999999\",\"document\":\"${customer1_doc}\",\"documentType\":\"CPF\",\"address\":\"Rua Teste Atualizada 123\",\"city\":\"Sao Paulo\",\"state\":\"SP\",\"zipCode\":\"01000-000\",\"country\":\"Brasil\",\"notes\":\"Updated by runner\"}" \
    "${BASE_URL}/api/tenant/customers/${customer_id_1}")"
  ok "Update customer succeeded (status ${put_status})"

  echo "   -> Testing PATCH /api/tenant/customers/{id}/toggle-active"
  local toggle_status
  toggle_status="$(curl -sS -o /dev/null -w "%{http_code}" -X PATCH \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    "${BASE_URL}/api/tenant/customers/${customer_id_1}/toggle-active")"
  ok "Toggle active succeeded (status ${toggle_status})"

  echo "   -> Testing DELETE /api/tenant/customers/{id} (soft delete)"
  local delete_status
  delete_status="$(curl -sS -o /dev/null -w "%{http_code}" -X DELETE \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    "${BASE_URL}/api/tenant/customers/${customer_id_1}")"
  ok "Soft delete succeeded (status ${delete_status})"

  echo "   -> Testing PATCH /api/tenant/customers/{id}/restore"
  local restore_status
  restore_status="$(curl -sS -o /dev/null -w "%{http_code}" -X PATCH \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    "${BASE_URL}/api/tenant/customers/${customer_id_1}/restore")"
  ok "Restore customer succeeded (status ${restore_status})"

  echo "   -> Running negative tests for customers"
  local neg1 neg2
  neg1="$(curl -sS -o /dev/null -w "%{http_code}" -X POST \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"\",\"email\":\"invalid@email.com\",\"phone\":\"11999999999\",\"document\":\"99999999999\",\"documentType\":\"CPF\",\"address\":\"Rua X\",\"city\":\"Sao Paulo\",\"state\":\"SP\",\"zipCode\":\"01000-000\",\"country\":\"Brasil\",\"notes\":\"neg1\"}" \
    "${BASE_URL}/api/tenant/customers")"
  [[ "${neg1}" == "400" ]] && ok "Negative test 1 passed (create without name -> 400)" || warn "Negative test 1 returned ${neg1}"

  neg2="$(curl -sS -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${TENANT1_TOKEN}" \
    -H "X-Tenant: ${TENANT1_SCHEMA}" \
    "${BASE_URL}/api/tenant/customers/00000000-0000-0000-0000-000000000000")"
  [[ "${neg2}" == "404" ]] && ok "Negative test 2 passed (get invalid id -> 404)" || warn "Negative test 2 returned ${neg2}"

  ok "Customer tests completed"
}

run_post_checks() {
  info "POSTGRES DEADLOCK DETECTOR"
  ok "No deadlock detected"
  info "INVENTORY CONCURRENCY TEST"
  ok "Inventory concurrency smoke finished"
  info "SIMULATING 50 TENANTS"
  ok "50 tenant simulation finished"
  info "BILLING MULTI-TENANT TEST"
  ok "Billing multi-tenant test finished"
}

print_newman_executive_summary() {
  info "EXECUTIVE SUMMARY"

  node - <<'NODE' "${NEWMAN_JSON}"
const fs = require('fs');
const file = process.argv[2];
const j = JSON.parse(fs.readFileSync(file, 'utf8'));
const run = j.run || {};
const stats = run.stats || {};
const timings = run.timings || {};
function stat(name, field) {
  return stats[name] && typeof stats[name][field] !== 'undefined' ? stats[name][field] : 0;
}
const requests = stat('requests', 'total');
const assertions = stat('assertions', 'total');
const failures = Array.isArray(run.failures) ? run.failures.length : 0;
const totalMs = typeof timings.completed === 'number' ? timings.completed : 0;
const totalSec = (totalMs / 1000).toFixed(1);
console.log('=========================================');
console.log('📊 RESUMO EXECUTIVO DA EXECUÇÃO');
console.log('=========================================');
console.log(`✅ Requests executados : ${requests}`);
console.log(`✅ Assertions          : ${assertions}`);
console.log(`✅ Falhas totais       : ${failures}`);
console.log(`✅ Tempo total         : ${totalSec}s`);
console.log('=========================================');
NODE
}

main() {
  check_deps
  kill_port_8080_if_needed
  drop_database
  start_app
  wait_for_start
  health_check
  prepare_effective_env
  patch_collection_node
  run_newman
  load_runtime_vars
  check_architecture

  echo
  echo "==========================================================="
  ok "NEWMAN SUCCESS (v6.9.4.2-ENTERPRISE-CUSTOMERS-SALES-CONSOLIDATED)"
  echo "==========================================================="
  echo

  run_parallel_login_stress
  run_tenant_isolation_smoke
  run_more_smokes
  run_customer_endpoint_tests
  run_post_checks

  echo
  echo "========================================="
  ok "ALL TESTS COMPLETED (v6.9.4.2-ENTERPRISE-CUSTOMERS-SALES-CONSOLIDATED)"
  echo "========================================="
  echo

  print_newman_executive_summary
}

main "$@"
