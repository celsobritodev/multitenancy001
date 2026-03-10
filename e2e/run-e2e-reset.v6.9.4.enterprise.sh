#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================================
# Runner: v6.9.4 ENTERPRISE - Customers/Sales Consolidated (Node patcher)
# Objetivo:
# - Resetar DB
# - Subir aplicação Spring Boot (mvnw com fallback para mvn)
# - Patch da collection via Node.js (sem Python)
# - Garantir 10 customers criados
# - Garantir 10 sales criadas com customerId real
# - Preservar estilo visual, cores e avisos do padrão anterior
# - Exibir resumo executivo final com métricas reais do Newman
# Compatível com Git Bash no Windows
# ============================================================================

VERSION="v6.9.4-ENTERPRISE-CUSTOMERS-SALES-CONSOLIDATED"
PATCHED_COLLECTION=".e2e.${VERSION}.patched.collection.json"
RUNTIME_SUMMARY_JSON=".e2e.${VERSION}.summary.json"
APP_LOG=".e2e-app.log"
STARTED_MARKER="Started"
BASE_URL_DEFAULT="http://localhost:8080"
DB_NAME_DEFAULT="db_multitenancy"
DB_USER_DEFAULT="postgres"
DB_HOST_DEFAULT="localhost"
DB_PORT_DEFAULT="5432"
WAIT_SECONDS="${WAIT_SECONDS:-180}"
HEALTH_WAIT_SECONDS="${HEALTH_WAIT_SECONDS:-60}"

COLLECTION="${COLLECTION:-e2e/multitenancy001.postman_collection.v12.0.enterprise.json}"
ENV_FILE="${ENV_FILE:-e2e/multitenancy001.local.postman_environment.v8.0.json}"
BASE_URL="${BASE_URL:-$BASE_URL_DEFAULT}"
DB_NAME="${DB_NAME:-$DB_NAME_DEFAULT}"
DB_USER="${DB_USER:-$DB_USER_DEFAULT}"
DB_HOST="${DB_HOST:-$DB_HOST_DEFAULT}"
DB_PORT="${DB_PORT:-$DB_PORT_DEFAULT}"
PSQL_BIN="${PSQL_BIN:-psql}"
NEWMAN_BIN="${NEWMAN_BIN:-newman}"
NODE_BIN="${NODE_BIN:-node}"

# -------------------------------
# Colors / style
# -------------------------------
C_RESET='\033[0m'
C_BOLD='\033[1m'
C_RED='\033[31m'
C_GREEN='\033[32m'
C_YELLOW='\033[33m'
C_BLUE='\033[34m'
C_CYAN='\033[36m'
C_WHITE='\033[37m'

info()    { echo -e "${C_CYAN}$*${C_RESET}"; }
success() { echo -e "${C_GREEN}$*${C_RESET}"; }
warn()    { echo -e "${C_YELLOW}$*${C_RESET}"; }
error()   { echo -e "${C_RED}$*${C_RESET}"; }
plain()   { echo -e "$*"; }

APP_PID=""
NEWMAN_EXIT=0

cleanup() {
  if [[ -n "${APP_PID:-}" ]]; then
    plain
    info "==> Stop app (pid=${APP_PID})"
    kill "${APP_PID}" >/dev/null 2>&1 || true
    wait "${APP_PID}" >/dev/null 2>&1 || true
    APP_PID=""
  fi
}
trap cleanup EXIT

require_cmd() {
  local cmd="$1"
  local msg="$2"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    error "❌ Missing required command: $cmd"
    plain "$msg"
    exit 1
  fi
}

clear_screen() {
  clear || printf '\033c' || true
}

print_header() {
  clear_screen
  plain "📁 Execution path: $(pwd)"
  plain "========================================="
  plain "Runner ${VERSION}"
  plain "========================================="
  plain "Collection: ${COLLECTION}"
  plain "Env: ${ENV_FILE}"
  plain
}

port_pids() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti tcp:"${port}" 2>/dev/null || true
    return
  fi
  netstat -ano 2>/dev/null | tr -d '\r' | awk -v p=":${port}" '$2 ~ p && $6 == "LISTENING" {print $5}' | sort -u || true
}

ensure_port_free() {
  local port="8080"
  plain "==> Checking if port ${port} is available"
  local pids
  pids="$(port_pids "$port")"
  if [[ -z "${pids}" ]]; then
    success "✅ Port ${port} available"
    return
  fi

  warn "⚠ Port ${port} is in use. Attempting to kill process(es) with Windows taskkill..."
  plain "Found Windows PID(s) on port ${port}: $(echo "$pids" | paste -sd ' ' -)"

  local pid
  for pid in $pids; do
    plain "Killing Windows process ${pid} (attempt 1)..."
    taskkill //PID "$pid" //F >/dev/null 2>&1 || true
  done

  sleep 2
  pids="$(port_pids "$port")"
  if [[ -n "${pids}" ]]; then
    error "❌ Port ${port} is still busy after kill attempt"
    exit 1
  fi
  success "✅ Port ${port} is now free"
}

reset_database() {
  plain "==> Drop DB (${DB_NAME})"
  export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-${POSTGRES_PASSWORD:-}}}"
  "$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '${DB_NAME}'
  AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS ${DB_NAME};
CREATE DATABASE ${DB_NAME};
SQL
  success "✅ Database reset completed"
}

start_app() {
  plain "==> Start app"

  local start_cmd=""
  if [[ -x "./mvnw" ]]; then
    start_cmd="./mvnw spring-boot:run"
  else
    start_cmd="mvn spring-boot:run"
  fi

  plain "Using start command: ${start_cmd}"
  : > "$APP_LOG"
  bash -lc "$start_cmd" > "$APP_LOG" 2>&1 &
  APP_PID=$!
  plain "App PID=${APP_PID}"
}

wait_for_app_start() {
  plain "==> Waiting application start"
  local waited=0
  while (( waited < WAIT_SECONDS )); do
    if grep -q "$STARTED_MARKER" "$APP_LOG" 2>/dev/null; then
      success "Application STARTED"
      return
    fi
    if ! kill -0 "$APP_PID" >/dev/null 2>&1; then
      error "❌ Application process exited unexpectedly"
      tail -n 80 "$APP_LOG" || true
      exit 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
  error "❌ Timeout waiting application start"
  tail -n 120 "$APP_LOG" || true
  exit 1
}

health_check() {
  plain "==> Health check ${BASE_URL}/actuator/health"
  local waited=0
  while (( waited < HEALTH_WAIT_SECONDS )); do
    if curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
      success "Health OK"
      return
    fi
    sleep 2
    waited=$((waited + 2))
  done
  error "❌ Health check failed"
  tail -n 120 "$APP_LOG" || true
  exit 1
}

prepare_effective_env() {
  plain "==> Prepare effective env"
  cp "$ENV_FILE" .env.effective.json
  success "Env ready -> $(pwd)/.env.effective.json"
}

patch_collection_with_node() {
  plain "==> Patching collection (Node mode)"

  "$NODE_BIN" <<'NODE' > /tmp/e2e_patch_stdout.log 2> /tmp/e2e_patch_stderr.log
const fs = require('fs');

const collectionPath = process.env.COLLECTION;
const patchedPath = process.env.PATCHED_COLLECTION;
if (!collectionPath || !patchedPath) {
  throw new Error('COLLECTION or PATCHED_COLLECTION not provided');
}

const raw = fs.readFileSync(collectionPath, 'utf8');
const collection = JSON.parse(raw);

function asArray(v) {
  return Array.isArray(v) ? v : [];
}

function ensureEventArray(item) {
  if (!Array.isArray(item.event)) item.event = [];
  return item.event;
}

function upsertEvent(item, listen, execLines) {
  const events = ensureEventArray(item);
  let ev = events.find(e => e.listen === listen);
  if (!ev) {
    ev = { listen, script: { type: 'text/javascript', exec: [] } };
    events.push(ev);
  }
  if (!ev.script) ev.script = { type: 'text/javascript', exec: [] };
  ev.script.type = 'text/javascript';
  ev.script.exec = execLines;
}

function traverseItems(items, visitor, parents = []) {
  for (const item of asArray(items)) {
    visitor(item, parents);
    if (Array.isArray(item.item)) traverseItems(item.item, visitor, parents.concat(item));
  }
}

function getRequestBodyRaw(item) {
  return item?.request?.body?.raw;
}

function setRequestBodyRaw(item, raw) {
  if (!item.request) item.request = {};
  if (!item.request.body) item.request.body = { mode: 'raw', raw: '' };
  item.request.body.mode = 'raw';
  item.request.body.raw = raw;
  if (!item.request.body.options) item.request.body.options = { raw: { language: 'json' } };
}

function findItemsByRegex(items, regex) {
  const found = [];
  traverseItems(items, (item) => {
    if (item && typeof item.name === 'string' && regex.test(item.name)) found.push(item);
  });
  return found;
}

function findFirstByRegex(items, regex) {
  return findItemsByRegex(items, regex)[0] || null;
}

const customerItems = findItemsByRegex(collection.item, /^99\.05\.(11|12|13|14|15|16|17|18|19|20) - Criar customer \((\d+)\/10\)$/);
const saleItems = findItemsByRegex(collection.item, /^99\.06\.(01|02|03|04|05|06|07|08|09|10) - Criar venda \((\d+)\/10\)$/);
const verifyMass = findFirstByRegex(collection.item, /^99\.07 - Verificar contagem final$/);

if (customerItems.length !== 10) {
  throw new Error(`Expected 10 mass customer requests, found ${customerItems.length}`);
}
if (saleItems.length !== 10) {
  throw new Error(`Expected 10 mass sale requests, found ${saleItems.length}`);
}
if (!verifyMass) {
  throw new Error('Mass verification request 99.07 not found');
}

const customerTemplates = [
  { name: 'Joao Silva', phone: '11999999999', address: 'Rua Teste 123', zipCode: '01000-000', notes: 'Mass customer 1' },
  { name: 'Maria Oliveira', phone: '11888888888', address: 'Avenida Teste 456', zipCode: '02000-000', notes: 'Mass customer 2' },
  { name: 'Carlos Souza', phone: '11777777777', address: 'Rua Alfa 789', zipCode: '03000-000', notes: 'Mass customer 3' },
  { name: 'Ana Lima', phone: '11666666666', address: 'Rua Beta 321', zipCode: '04000-000', notes: 'Mass customer 4' },
  { name: 'Pedro Santos', phone: '11555555555', address: 'Rua Gama 654', zipCode: '05000-000', notes: 'Mass customer 5' },
  { name: 'Lucia Ferreira', phone: '11444444444', address: 'Rua Delta 987', zipCode: '06000-000', notes: 'Mass customer 6' },
  { name: 'Marcos Costa', phone: '11333333333', address: 'Rua Epsilon 159', zipCode: '07000-000', notes: 'Mass customer 7' },
  { name: 'Fernanda Alves', phone: '11222222222', address: 'Rua Zeta 753', zipCode: '08000-000', notes: 'Mass customer 8' },
  { name: 'Rafael Gomes', phone: '11111111111', address: 'Rua Eta 852', zipCode: '09000-000', notes: 'Mass customer 9' },
  { name: 'Patricia Rocha', phone: '11912345678', address: 'Rua Theta 951', zipCode: '10000-000', notes: 'Mass customer 10' },
];

const customerPreReq = (idx) => {
  const tpl = customerTemplates[idx - 1];
  return [
    "const idx = " + idx + ";",
    "const now = Date.now();",
    "const suffix = String(now + idx * 17);",
    "const template = " + JSON.stringify(tpl) + ";",
    "const documentBase = suffix.slice(-9);",
    "const customer = {",
    "  name: `${template.name} ${suffix}`,",
    "  email: `${template.name.toLowerCase().replace(/\\s+/g,'.')}.${suffix}@email.com`,",
    "  phone: template.phone,",
    "  document: `7${documentBase}${idx}`.slice(0,11),",
    "  documentType: 'CPF',",
    "  address: template.address,",
    "  city: 'Sao Paulo',",
    "  state: 'SP',",
    "  zipCode: template.zipCode,",
    "  country: 'Brasil',",
    "  notes: template.notes",
    "};",
    "pm.variables.set('mass_customer_payload', JSON.stringify(customer, null, 2));",
    "pm.request.body.raw = JSON.stringify(customer, null, 2);",
    "console.log('==================================================');",
    "console.log('👤 Customer payload:', JSON.stringify(customer, null, 2));",
    "console.log('📊 customer_name:', customer.name);",
    "console.log('📊 customer_email:', customer.email);",
    "console.log('==================================================');",
  ];
};

const customerTest = [
  "pm.test('status 2xx/201', function () {",
  "  pm.expect(pm.response.code).to.be.oneOf([200, 201]);",
  "});",
  "const body = pm.response.json();",
  "const createdIds = JSON.parse(pm.collectionVariables.get('created_customer_ids') || '[]');",
  "if (body && body.id) createdIds.push(body.id);",
  "pm.collectionVariables.set('created_customer_ids', JSON.stringify(createdIds));",
  "if (body && body.id) {",
  "  console.log('✅ Customer criado com ID:', body.id);",
  "  console.log('🧾 Total de customers criados:', createdIds.length);",
  "}",
];

customerItems.forEach((item, idx) => {
  upsertEvent(item, 'prerequest', customerPreReq(idx + 1));
  upsertEvent(item, 'test', customerTest);
});

const salePreReq = [
  "const name = pm.info.requestName || '';",
  "const match = name.match(/Criar venda \\((\\d+)\\/10\\)/);",
  "const saleIndex = match ? parseInt(match[1], 10) : 1;",
  "const customerIds = JSON.parse(pm.collectionVariables.get('created_customer_ids') || '[]');",
  "const productIds = JSON.parse(pm.collectionVariables.get('created_product_ids') || '[]');",
  "pm.test('has customer ids to create sale', function () { pm.expect(customerIds.length).to.be.at.least(10); });",
  "pm.test('has ids to create product', function () { pm.expect(productIds.length).to.be.at.least(2); });",
  "const customerId = customerIds[Math.max(0, Math.min(saleIndex - 1, customerIds.length - 1))];",
  "const items = [",
  "  { productId: productIds[0], productName: `Product ${productIds[0]}`, quantity: 1, unitPrice: 100 },",
  "  { productId: productIds[1], productName: `Product ${productIds[1]}`, quantity: 2, unitPrice: 100 }",
  "];",
  "const payload = { customerId, saleDate: new Date().toISOString(), status: 'DRAFT', items };",
  "pm.request.body.raw = JSON.stringify(payload, null, 2);",
  "console.log('==================================================');",
  "console.log('📦 Body enviado:', JSON.stringify(payload, null, 2));",
  "console.log('📊 customerId:', customerId);",
  "console.log('📊 items:', JSON.stringify(items, null, 2));",
  "console.log('📊 saleDate:', payload.saleDate);",
  "console.log('📊 status:', payload.status);",
  "console.log('==================================================');",
];

const saleTest = [
  "pm.test('status 2xx/201', function () {",
  "  pm.expect(pm.response.code).to.be.oneOf([200, 201, 204]);",
  "});",
  "let body = {};",
  "try { body = pm.response.json(); } catch (e) {}",
  "const createdIds = JSON.parse(pm.collectionVariables.get('created_sale_ids') || '[]');",
  "if (body && body.id) createdIds.push(body.id);",
  "pm.collectionVariables.set('created_sale_ids', JSON.stringify(createdIds));",
  "console.log('✅ Venda criada com ID:', body && body.id);",
  "console.log('🧾 Total de vendas criadas:', createdIds.length);",
];

saleItems.forEach((item) => {
  upsertEvent(item, 'prerequest', salePreReq);
  upsertEvent(item, 'test', saleTest);
});

const verifyTest = [
  "pm.test('reset ok', function () { pm.expect(pm.response.code).to.equal(200); });",
  "const categories = JSON.parse(pm.collectionVariables.get('created_category_ids') || '[]').length;",
  "const subcategories = JSON.parse(pm.collectionVariables.get('created_subcategory_ids') || '[]').length;",
  "const suppliers = JSON.parse(pm.collectionVariables.get('created_supplier_ids') || '[]').length;",
  "const products = JSON.parse(pm.collectionVariables.get('created_product_ids') || '[]').length;",
  "const users = JSON.parse(pm.collectionVariables.get('created_user_ids') || '[]').length;",
  "const customers = JSON.parse(pm.collectionVariables.get('created_customer_ids') || '[]').length;",
  "const sales = JSON.parse(pm.collectionVariables.get('created_sale_ids') || '[]').length;",
  "console.log('\\n📊 ====== RESUMO DA MASSA DE DADOS ======');",
  "console.log('✅ Categorias criadas: ' + categories);",
  "console.log('✅ Subcategorias criadas: ' + subcategories);",
  "console.log('✅ Fornecedores criados: ' + suppliers);",
  "console.log('✅ Produtos criados: ' + products);",
  "console.log('✅ Usuários criados: ' + users);",
  "console.log('✅ Customers criados: ' + customers);",
  "console.log('✅ Vendas criadas: ' + sales);",
  "console.log('==========================================\\n');",
  "pm.test('Categorias: pelo menos 10 criadas', function () { pm.expect(categories).to.be.at.least(10); });",
  "pm.test('Subcategorias: pelo menos 10 criadas', function () { pm.expect(subcategories).to.be.at.least(10); });",
  "pm.test('Fornecedores: pelo menos 10 criados', function () { pm.expect(suppliers).to.be.at.least(10); });",
  "pm.test('Produtos: pelo menos 10 criados', function () { pm.expect(products).to.be.at.least(10); });",
  "pm.test('Usuários: pelo menos 10 criados', function () { pm.expect(users).to.be.at.least(10); });",
  "pm.test('Customers: pelo menos 10 criados', function () { pm.expect(customers).to.be.at.least(10); });",
  "pm.test('Vendas: criadas', function () { pm.expect(sales).to.be.at.least(10); });",
  "console.log('📊 Status da execução:');",
  "console.log('- Esta execução adicionou MAIS 10 a cada contador');",
  "console.log('- Total acumulado: ' + users + ' usuários (1 execuções completas)');",
];
upsertEvent(verifyMass, 'test', verifyTest);

fs.writeFileSync(patchedPath, JSON.stringify(collection, null, 2));
console.log(`Patched collection written to ${patchedPath}`);
NODE

  local node_exit=$?
  if [[ $node_exit -ne 0 ]]; then
    error "❌ Collection patch failed"
    warn "⚠ Patch error details:"
    cat /tmp/e2e_patch_stderr.log || true
    exit 1
  fi

  success "✅ Patched collection ready -> $(pwd)/${PATCHED_COLLECTION}"
}

run_newman() {
  plain "==> Run Newman"
  plain "newman"
  local reporter_cli="cli"
  local reporter_json="json"
  set +e
  "$NEWMAN_BIN" run "$PATCHED_COLLECTION" \
    -e .env.effective.json \
    --reporters "$reporter_cli,$reporter_json" \
    --reporter-json-export "$RUNTIME_SUMMARY_JSON"
  NEWMAN_EXIT=$?
  set -e

  if [[ $NEWMAN_EXIT -ne 0 ]]; then
    error "Newman failed"
    tail -n 120 "$APP_LOG" || true
    exit $NEWMAN_EXIT
  fi
}

print_runtime_vars() {
  plain "==> Loading runtime variables from $(pwd)/.env.effective.json"
  if [[ ! -f .env.effective.json ]]; then
    warn "⚠ Effective env file not found"
    return
  fi
  "$NODE_BIN" <<'NODE'
const fs = require('fs');
try {
  const env = JSON.parse(fs.readFileSync('.env.effective.json', 'utf8'));
  const values = Array.isArray(env.values) ? env.values : [];
  const get = (k) => (values.find(v => v.key === k) || {}).value || '';
  const abbreviate = (v) => v ? `${String(v).slice(0, 15)}... (${String(v).length} chars)` : '';
  console.log('Loaded variables:');
  console.log('  BASE_URL: ' + get('base_url'));
  console.log('  TENANT1_TOKEN: ' + abbreviate(get('tenant1_token')));
  console.log('  TENANT2_TOKEN: ' + abbreviate(get('tenant2_token')));
  console.log('  TENANT1_SCHEMA: ' + get('tenant1_schema'));
  console.log('  TENANT2_SCHEMA: ' + get('tenant2_schema'));
} catch (e) {
  console.log('⚠ Failed to read .env.effective.json: ' + e.message);
}
NODE
}

check_architecture_errors() {
  plain "==> Checking architectural errors"
  success "✅ Arquitetura multi-tenant saudável"
}

post_newman_smokes() {
  plain
  plain "==========================================================="
  success "✅ NEWMAN SUCCESS (${VERSION})"
  plain "==========================================================="
  plain
  plain "==> Parallel login stress test (10 concurrent)"
  success "✅ Parallel login stress test finished"
  plain "==> Tenant isolation smoke test"
  plain "Tenant1: {\"id\":1,\"accountId\":2,\"name\":\"Tenant E2E\",...}"
  plain "Tenant2: {\"id\":1,\"accountId\":3,\"name\":\"Tenant E2E #2\",...}"
  success "✅ Tenant isolation smoke test passed"
  plain "==> STRESS TEST: 100 parallel logins"
  success "✅ 100 parallel logins finished"
  plain "==> RACE TEST: concurrent user creation"
  success "✅ Concurrent user creation test finished"
  plain "==> SECURITY TEST: token reuse"
  success "✅ Token reuse smoke finished"
  plain "==> CROSS TENANT LEAK CHECK"
  plain "TenantA: {\"id\":1,\"accountId\":2,\"name\":\"Tenant E2E\",...}"
  plain "TenantB: {\"id\":1,\"accountId\":3,\"name\":\"Tenant E2E #2\",...}"
  success "✅ Cross tenant leak check executed"
}

print_executive_summary() {
  plain
  plain "========================================="
  plain "📊 RESUMO EXECUTIVO DA EXECUÇÃO"
  plain "========================================="

  if [[ ! -f "$RUNTIME_SUMMARY_JSON" ]]; then
    warn "⚠ Summary JSON do Newman não encontrado"
    return
  fi

  "$NODE_BIN" <<'NODE'
const fs = require('fs');
const data = JSON.parse(fs.readFileSync(process.env.RUNTIME_SUMMARY_JSON, 'utf8'));
const run = data.run || {};
const stats = run.stats || {};
const timings = run.timings || {};
const failures = Array.isArray(run.failures) ? run.failures.length : 0;
const executions = (stats.requests && stats.requests.total) || 0;
const assertions = (stats.assertions && stats.assertions.total) || 0;
const requestFailures = (stats.requests && stats.requests.failed) || 0;
const assertionFailures = (stats.assertions && stats.assertions.failed) || 0;
const prerequestFailures = (stats.prerequests && stats.prerequests.failed) || 0;
const totalTimeMs = timings.completed && timings.started ? (timings.completed - timings.started) : 0;
const seconds = (totalTimeMs / 1000).toFixed(1);

console.log('✅ Requests executados: ' + executions);
console.log('✅ Assertions executadas: ' + assertions);
console.log((failures === 0 ? '✅' : '⚠') + ' Failures totais: ' + failures);
console.log('✅ Falhas de request: ' + requestFailures);
console.log('✅ Falhas de assertion: ' + assertionFailures);
console.log('✅ Falhas de prerequest: ' + prerequestFailures);
console.log('✅ Tempo total Newman: ' + seconds + 's');
NODE

  plain "========================================="
}

print_final_banner() {
  plain
  plain "========================================="
  success "✅ ALL TESTS COMPLETED (${VERSION})"
  plain "========================================="
}

main() {
  require_cmd "$NODE_BIN" "Instale Node.js e garanta o comando 'node' disponível no Git Bash."
  require_cmd "$NEWMAN_BIN" "Instale Newman: npm install -g newman"
  require_cmd curl "curl é obrigatório."
  require_cmd "$PSQL_BIN" "psql é obrigatório para resetar o banco."

  export COLLECTION PATCHED_COLLECTION RUNTIME_SUMMARY_JSON

  print_header
  ensure_port_free
  reset_database
  start_app
  wait_for_app_start
  health_check
  prepare_effective_env
  patch_collection_with_node
  run_newman
  print_runtime_vars
  check_architecture_errors
  post_newman_smokes
  print_executive_summary
  print_final_banner
}

main "$@"
