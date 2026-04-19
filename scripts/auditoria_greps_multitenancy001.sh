#!/usr/bin/env bash

# Auditoria arquitetural rápida por grep para o projeto multitenancy001
# Objetivo: identificar sinais de problemas recorrentes de arquitetura, tempo,
# transações, boundary, exceptions, controllers e naming.

set -u

clear

# =========================
# CORES
# =========================
if command -v tput >/dev/null 2>&1 && [ -n "${TERM:-}" ]; then
  BOLD="$(tput bold)"
  RED="$(tput setaf 1)"
  GREEN="$(tput setaf 2)"
  YELLOW="$(tput setaf 3)"
  BLUE="$(tput setaf 4)"
  MAGENTA="$(tput setaf 5)"
  CYAN="$(tput setaf 6)"
  RESET="$(tput sgr0)"
else
  BOLD=""
  RED=""
  GREEN=""
  YELLOW=""
  BLUE=""
  MAGENTA=""
  CYAN=""
  RESET=""
fi

# =========================
# CONFIG
# =========================
# =========================
# DETECÇÃO INTELIGENTE DA RAIZ DO PROJETO
# =========================
if [ -n "${1:-}" ]; then
  ROOT_DIR="$1"
else
  if [ -d "src/main/java" ]; then
    ROOT_DIR="$(pwd)"
  elif [ -d "../src/main/java" ]; then
    ROOT_DIR="$(cd .. && pwd)"
  elif [ -d "../../src/main/java" ]; then
    ROOT_DIR="$(cd ../.. && pwd)"
  else
    echo "✖ Não consegui localizar src/main/java automaticamente"
    echo "▶ Use: ./auditoria_greps_multitenancy001.sh /caminho/do/projeto"
    exit 1
  fi
fi

SRC_DIR="$ROOT_DIR/src/main/java"

# =========================
# FUNCOES
# =========================
print_line() {
  printf "%b\n" "${BLUE}--------------------------------------------------------------------------------${RESET}"
}

print_title() {
  print_line
  printf "%b\n" "${BOLD}${CYAN}$1${RESET}"
  print_line
}

print_info() {
  printf "%b\n" "${MAGENTA}▶ $1${RESET}"
}

print_ok() {
  printf "%b\n" "${GREEN}✔ $1${RESET}"
}

print_warn() {
  printf "%b\n" "${YELLOW}⚠ $1${RESET}"
}

print_err() {
  printf "%b\n" "${RED}✖ $1${RESET}"
}

show_command() {
  printf "%b\n" "${BOLD}${CYAN}$ $1${RESET}"
}

run_grep() {
  local title="$1"
  local objective="$2"
  local cmd="$3"

  printf "\n"
  print_title "$title"
  print_info "$objective"
  show_command "$cmd"
  printf "\n"

  bash -lc "cd \"$ROOT_DIR\" && $cmd" || true
  printf "\n"
}

count_matches() {
  local cmd="$1"
  bash -lc "cd \"$ROOT_DIR\" && $cmd" 2>/dev/null | wc -l | tr -d ' '
}

summary_line() {
  local label="$1"
  local count="$2"
  if [ "$count" = "0" ]; then
    printf "%b\n" "${GREEN}✔ ${label}: 0 ocorrência(s)${RESET}"
  else
    printf "%b\n" "${YELLOW}⚠ ${label}: ${count} ocorrência(s)${RESET}"
  fi
}

# =========================
# VALIDACAO INICIAL
# =========================
print_title "AUDITORIA ARQUITETURAL POR GREP — MULTITENANCY001"
printf "%b\n" "${BOLD}Diretório alvo:${RESET} $ROOT_DIR"

if [ ! -d "$SRC_DIR" ]; then
  print_err "Não encontrei $SRC_DIR"
  print_info "Use assim: ./auditoria_greps_multitenancy001.sh /c/Users/Usuario/eclipse-workspace/multitenancy001"
  exit 1
fi

print_ok "Projeto localizado com sucesso."

# =========================
# BLOCO 1 — TEMPO
# =========================
run_grep \
  "[1/14] TEMPO — LocalDateTime" \
  "Objetivo: garantir que LocalDateTime não esteja sendo usado no código Java." \
  "grep -R --line-number --color=always 'LocalDateTime' src/main/java"

run_grep \
  "[2/14] TEMPO — Instant.now()" \
  "Objetivo: localizar uso direto de Instant.now() e validar se AppClock continua sendo a única fonte de tempo." \
  "grep -R --line-number --color=always 'Instant.now()' src/main/java"

# =========================
# BLOCO 2 — EXCEPTIONS
# =========================
run_grep \
  "[3/14] EXCEPTIONS — Uso de ApiException" \
  "Objetivo: verificar se os erros de regra estão padronizados com ApiException." \
  "grep -R --line-number --color=always 'throw new ApiException' src/main/java"

run_grep \
  "[4/14] EXCEPTIONS — RuntimeException" \
  "Objetivo: localizar exceções genéricas que merecem revisão para ApiException ou exceção mais semântica." \
  "grep -R --line-number --color=always 'RuntimeException' src/main/java"

run_grep \
  "[5/14] EXCEPTIONS — @ExceptionHandler e GlobalExceptionHandler" \
  "Objetivo: revisar a estratégia central de tratamento de exceções." \
  "grep -R --line-number --color=always '@ExceptionHandler\|GlobalExceptionHandler' src/main/java"

# =========================
# BLOCO 3 — BOUNDARY / MULTITENANCY
# =========================
run_grep \
  "[6/14] BOUNDARY — TenantToPublicBridgeExecutor" \
  "Objetivo: localizar todos os pontos onde o tenant cruza explicitamente para o schema public/control plane." \
  "grep -R --line-number --color=always 'TenantToPublicBridgeExecutor' src/main/java"

run_grep \
  "[7/14] BOUNDARY — Executors e UnitOfWork" \
  "Objetivo: revisar uso de executors/bridges/units of work para crossing controlado entre contextos." \
  "grep -R --line-number --color=always 'PublicSchemaExecutor\|PublicSchemaUnitOfWork\|TenantContextExecutor\|TenantToPublicBridgeExecutor' src/main/java"

run_grep \
  "[8/14] BOUNDARY — Repositories de outro contexto dentro de services" \
  "Objetivo: inspecionar uso de Repository em services para procurar dependências suspeitas entre contextos." \
  "grep -R --line-number --color=always 'Repository' src/main/java | grep '/app/'"

# =========================
# BLOCO 4 — CONTROLLERS / API
# =========================
run_grep \
  "[9/14] API — Controller chamando Repository" \
  "Objetivo: detectar referência a Repository dentro de classes do pacote api/controller." \
  "grep -R --line-number --color=always 'Repository' src/main/java | grep '/api/'"

run_grep \
  "[10/14] API — @RequestBody" \
  "Objetivo: revisar entradas de controller para confirmar uso de DTO e evitar Entity em RequestBody." \
  "grep -R --line-number --color=always '@RequestBody' src/main/java"

run_grep \
  "[11/14] API — Controller compliance / architecture enforcement" \
  "Objetivo: revisar verificadores automáticos de conformidade de controllers." \
  "grep -R --line-number --color=always 'ControllerComplianceVerifier\|ControllerComplianceExempt' src/main/java"

# =========================
# BLOCO 5 — JSON / AUDITORIA
# =========================
run_grep \
  "[12/14] AUDITORIA — JSON manual suspeito" \
  "Objetivo: localizar indícios de JSON escrito na mão; revisar se é código real ou apenas comentário/Javadoc/fallback controlado." \
  "grep -R --line-number --color=always '{\\\"' src/main/java"

run_grep \
  "[13/14] AUDITORIA — Mapper e infraestrutura de audit" \
  "Objetivo: revisar uso de JsonDetailsMapper, AuditDetails e infraestrutura central de auditoria." \
  "grep -R --line-number --color=always 'JsonDetailsMapper\|AuditDetails\|AuditService\|SecurityAudit\|AuthEventAudit' src/main/java"

# =========================
# BLOCO 6 — TRANSAÇÕES
# =========================
run_grep \
  "[14/14] TRANSAÇÕES — @TenantTx / @TenantReadOnlyTx / @PublicTx / @PublicReadOnlyTx / @Transactional" \
  "Objetivo: revisar disciplina transacional do projeto, com foco em write/read e uso de anotações específicas por contexto." \
  "grep -R --line-number --color=always '@TenantTx\|@TenantReadOnlyTx\|@PublicTx\|@PublicReadOnlyTx\|@Transactional' src/main/java"

# =========================
# BLOCO EXTRA — NAMING / SEMANTICA
# =========================
run_grep \
  "[EXTRA 1] NAMING — Facade / Helper / Resolver / Sync / Scheduler" \
  "Objetivo: mapear a semântica atual da app layer e identificar candidatos a padronização de naming." \
  "grep -R --line-number --color=always 'class .*Facade\|class .*Helper\|class .*Resolver\|class .*SyncService\|class .*Scheduler\|class .*LifecycleService\|class .*CommandService\|class .*QueryService' src/main/java"

run_grep \
  "[EXTRA 2] ENUM CANDIDATES — String status/type/mode/origin" \
  "Objetivo: localizar campos String que podem virar enum para aumentar tipagem e semântica." \
  "grep -R --line-number --color=always 'String status\|String type\|String mode\|String origin' src/main/java"

# =========================
# RESUMO FINAL
# =========================
print_title "RESUMO RÁPIDO"

summary_line "LocalDateTime" "$(count_matches "grep -R 'LocalDateTime' src/main/java")"
summary_line "Instant.now()" "$(count_matches "grep -R 'Instant.now()' src/main/java")"
summary_line "RuntimeException" "$(count_matches "grep -R 'RuntimeException' src/main/java")"
summary_line "Controller chamando Repository" "$(count_matches "grep -R 'Repository' src/main/java | grep '/api/'")"
summary_line "JSON manual suspeito ({\\\" )" "$(count_matches "grep -R '{\\\"' src/main/java")"

printf "\n"
print_ok "Auditoria concluída."
printf "%b\n" "${BOLD}${CYAN}Dica:${RESET} salve a saída com: ./auditoria_greps_multitenancy001.sh | tee auditoria_greps_saida.txt"
printf "\n"
