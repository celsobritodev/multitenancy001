#!/bin/bash
set -euo pipefail

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Limpa a tela
clear

# Mostra banner de início
echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}${BOLD}           GERADOR DE CONTEXTO PARA ANÁLISE IA                ${NC}"
echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo ""

# Mostra informações de execução
echo -e "${YELLOW}${BOLD}📍 INFORMAÇÕES DE EXECUÇÃO${NC}"
echo -e "${YELLOW}─────────────────────────────────────────────────${NC}"
echo -e "${GREEN}• Diretório de execução:${NC} $(pwd)"
echo -e "${GREEN}• Data/Hora:${NC} $(date '+%Y-%m-%d %H:%M:%S')"
echo -e "${GREEN}• Usuário:${NC} $(whoami)"
echo -e "${GREEN}• Hostname:${NC} $(hostname 2>/dev/null || echo 'N/A')"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Função para converter caminho absoluto para relativo (em relação ao diretório atual)
get_relative_path() {
    local absolute_path="$1"
    local current_dir="$(pwd)"
    local relative_path=""
    
    # Se for o mesmo diretório, retorna .
    if [ "$absolute_path" = "$current_dir" ]; then
        echo "."
        return
    fi
    
    # Tenta usar realpath --relative-to se disponível (Linux)
    if command -v realpath &> /dev/null; then
        realpath --relative-to="$current_dir" "$absolute_path" 2>/dev/null && return
    fi
    
    # Fallback: implementação simples de caminho relativo
    local common_part="$current_dir"
    local back_part=""
    local result_part="${absolute_path}"
    
    while [ "${result_part#"$common_part"}" = "${result_part}" ]; do
        common_part="$(dirname "$common_part")"
        if [ "$common_part" = "/" ] || [ "$common_part" = "." ]; then
            back_part="../${back_part}"
            common_part="$current_dir"
            break
        fi
        back_part="../${back_part}"
    done
    
    relative_path="${back_part}${result_part#"$common_part"}"
    relative_path="${relative_path#./}"
    
    if [ -z "$relative_path" ]; then
        echo "."
    else
        echo "$relative_path"
    fi
}

echo -e "${YELLOW}${BOLD}📁 ESTRUTURA DE DIRETÓRIOS (RELATIVO)${NC}"
echo -e "${YELLOW}─────────────────────────────────────────────────${NC}"
echo -e "${GREEN}• Diretório do script:${NC} $(get_relative_path "$SCRIPT_DIR")"
echo -e "${GREEN}• Diretório do projeto:${NC} $(get_relative_path "$PROJECT_DIR")"
echo ""

# Generate 4 random uppercase letters
generate_random_suffix() {
    echo -e "${BLUE}⏳ Gerando sufixo aleatório...${NC}" >&2
    # Using /dev/urandom to generate random letters
    # This works on Linux/Mac/Git Bash for Windows
    LC_ALL=C tr -dc 'A-Z' < /dev/urandom 2>/dev/null | head -c 4 || {
        # Fallback for systems without /dev/urandom (rare)
        echo "$(date +%N | sha256sum | base64 | tr -dc 'A-Z' | head -c 4)"
    }
}

echo -e "${BLUE}${BOLD}🔧 CONFIGURAÇÕES${NC}"
echo -e "${BLUE}─────────────────────────────────────────────────${NC}"
RANDOM_SUFFIX=$(generate_random_suffix)
# Ensure we always have 4 chars (in case of fallback issues)
while [ ${#RANDOM_SUFFIX} -lt 4 ]; do
    RANDOM_SUFFIX="${RANDOM_SUFFIX}A"
done
echo -e "${GREEN}• Sufixo gerado:${NC} $RANDOM_SUFFIX"

# Output outside the project (avoid Eclipse search/index pollution)
OUTPUT_DIR="$HOME/eclipse-workspace/ConteudoAnaliseIA"
mkdir -p "$OUTPUT_DIR"
OUTPUT_FILE="$OUTPUT_DIR/contexto_multitenancy${RANDOM_SUFFIX}.txt"

# Options (0/1)
INCLUDE_TESTS="${INCLUDE_TESTS:-0}"     # 1 to include src/test
STRIP_COMMENTS="${STRIP_COMMENTS:-0}"   # 1 to remove comments (only if you need to shrink a lot)

echo -e "${GREEN}• Incluir testes:${NC} $([ "$INCLUDE_TESTS" = "1" ] && echo "SIM" || echo "NÃO")"
echo -e "${GREEN}• Remover comentários:${NC} $([ "$STRIP_COMMENTS" = "1" ] && echo "SIM" || echo "NÃO")"
echo ""

echo -e "${YELLOW}${BOLD}📄 ARQUIVO DE SAÍDA${NC}"
echo -e "${YELLOW}─────────────────────────────────────────────────${NC}"
echo -e "${GREEN}• Diretório:${NC} $(get_relative_path "$OUTPUT_DIR")"
echo -e "${GREEN}• Arquivo:${NC} $(basename "$OUTPUT_FILE")"
echo -e "${GREEN}• Caminho completo:${NC} $OUTPUT_FILE"
echo ""

echo -e "${MAGENTA}${BOLD}⚙️  PROCESSANDO ARQUIVOS...${NC}"
echo -e "${MAGENTA}─────────────────────────────────────────────────${NC}"

# Clear output file
: > "$OUTPUT_FILE"

write_header () {
  echo "================================================================" >> "$OUTPUT_FILE"
  echo "$1" >> "$OUTPUT_FILE"
  echo "================================================================" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
}

# 0) SUMMARY (ASCII only)
write_header "PROJECT: $(basename "$PROJECT_DIR")"
cat >> "$OUTPUT_FILE" << 'EOF'
### PROJECT SUMMARY
- Stack: Java + Spring Boot + JPA + Flyway + Security
- Pattern: DDD layered (no ports & adapters)
- Multi-tenancy: control plane (public schema) + tenant schema per account
- Source of truth: Flyway migrations (DB dropped and recreated)
EOF
echo "" >> "$OUTPUT_FILE"
echo -e "${GREEN}✅ Resumo do projeto adicionado${NC}"

# Helper: stable list of "included" files (code + migrations + config)
build_file_list () {
  local -a expr=(
    -type f
    -not -path "*/target/*"
    -not -path "*/.git/*"
    -not -path "*/.idea/*"
    -not -path "*/.vscode/*"
    -not -path "*/node_modules/*"
    -not -path "*/build/*"
    -not -path "*/dist/*"
    -not -path "*/out/*"
    -not -path "*/bin/*"
    -not -path "*/.mvn/*"
    \( -name "*.java"
       -o -path "$PROJECT_DIR/src/main/resources/db/migration/**/V*.sql"
       -o -path "$PROJECT_DIR/pom.xml"
       -o -path "$PROJECT_DIR/src/main/resources/application.properties"
       -o -path "$PROJECT_DIR/src/main/resources/application-*.properties"
       -o -path "$PROJECT_DIR/README.md"
       -o -path "$PROJECT_DIR/ARCHITECTURE.md"
    \)
  )

  if [ "$INCLUDE_TESTS" != "1" ]; then
    expr+=( -not -path "$PROJECT_DIR/src/test/*" )
  fi

  # Print absolute paths
  find "$PROJECT_DIR" "${expr[@]}" | sort
}

# 1) INDEX (ONLY what matters)
echo -e "${BLUE}📑 Gerando índice de arquivos...${NC}"
write_header "FILE MAP (INDEX - CODE + MIGRATIONS + CONFIG ONLY)"
{
  echo "pom.xml"
  # List only the relevant roots (avoid logs/docx/png/etc)
  if [ -d "$PROJECT_DIR/src/main/java" ]; then
    find "$PROJECT_DIR/src/main/java" -type f -name "*.java" | sed "s|^$PROJECT_DIR/||" | sort
  fi

  if [ -d "$PROJECT_DIR/src/main/resources" ]; then
    find "$PROJECT_DIR/src/main/resources" -type f \
      \( -name "application.properties" -o -name "application-*.properties" -o -path "$PROJECT_DIR/src/main/resources/db/migration/**/V*.sql" \) \
      | sed "s|^$PROJECT_DIR/||" | sort
  fi

  if [ "$INCLUDE_TESTS" = "1" ] && [ -d "$PROJECT_DIR/src/test/java" ]; then
    find "$PROJECT_DIR/src/test/java" -type f -name "*.java" | sed "s|^$PROJECT_DIR/||" | sort
  fi
} >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo -e "${GREEN}✅ Índice gerado com sucesso${NC}"

# 2) INCLUDED FILES (flat list)
echo -e "${BLUE}📋 Listando arquivos incluídos...${NC}"
write_header "INCLUDED FILES (FILTERED LIST)"
build_file_list | sed "s|^$PROJECT_DIR/||" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
FILE_COUNT=$(build_file_list | wc -l)
echo -e "${GREEN}✅ $FILE_COUNT arquivos identificados${NC}"
echo ""

emit_file_content () {
  local f="$1"
  if [ "$STRIP_COMMENTS" = "1" ]; then
    # Remove // and /* */ comments (simple; may have edge cases)
    sed 's/\r$//' "$f" \
      | perl -0777 -pe 's!/\*.*?\*/!!gs; s!//.*$!!gm' \
      | sed '/^[[:space:]]*$/d'
  else
    sed 's/\r$//' "$f"
  fi
}

# 3) CONTENT
echo -e "${BLUE}📝 Processando conteúdo dos arquivos...${NC}"
write_header "FILE CONTENTS"

# Priority: docs/config first (if present)
PRIORITY_FILES=()
[ -f "$PROJECT_DIR/README.md" ] && PRIORITY_FILES+=("$PROJECT_DIR/README.md")
[ -f "$PROJECT_DIR/ARCHITECTURE.md" ] && PRIORITY_FILES+=("$PROJECT_DIR/ARCHITECTURE.md")
[ -f "$PROJECT_DIR/pom.xml" ] && PRIORITY_FILES+=("$PROJECT_DIR/pom.xml")
[ -f "$PROJECT_DIR/src/main/resources/application.properties" ] && PRIORITY_FILES+=("$PROJECT_DIR/src/main/resources/application.properties")
for p in "$PROJECT_DIR"/src/main/resources/application-*.properties; do
  [ -f "$p" ] && PRIORITY_FILES+=("$p")
done

echo -e "${GREEN}📌 Processando arquivos prioritários...${NC}"
priority_count=0
for f in "${PRIORITY_FILES[@]}"; do
  rel="${f#$PROJECT_DIR/}"
  echo "### FILE: $rel" >> "$OUTPUT_FILE"
  emit_file_content "$f" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
  echo "### END FILE: $rel" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
  priority_count=$((priority_count + 1))
  echo -e "${GREEN}  • Processado:${NC} $rel"
done
echo -e "${GREEN}✅ $priority_count arquivos prioritários processados${NC}"
echo ""

# Remaining files (excluding priority)
echo -e "${BLUE}📦 Processando demais arquivos...${NC}"
remaining_count=0
total_files=$FILE_COUNT
current=0

# Função para atualizar a porcentagem na mesma linha
update_progress() {
    local current=$1
    local total=$2
    local percent=$((current * 100 / total))
    echo -ne "\r${YELLOW}  ⏳ Progresso: ["
    
    # Barra de progresso
    local bar_size=30
    local filled=$((percent * bar_size / 100))
    for ((i=0; i<bar_size; i++)); do
        if [ $i -lt $filled ]; then
            echo -ne "█"
        else
            echo -ne "░"
        fi
    done
    
    echo -ne "] ${percent}% (${current}/${total})${NC}"
}

# Inicializa a barra de progresso
update_progress 0 $total_files

build_file_list | while IFS= read -r f; do
  skip=0
  for pf in "${PRIORITY_FILES[@]}"; do
    [ "$f" = "$pf" ] && skip=1 && break
  done
  [ "$skip" = "1" ] && continue

  rel="${f#$PROJECT_DIR/}"
  current=$((current + 1))
  
  # Atualiza a barra de progresso
  update_progress $current $total_files
  
  echo "### FILE: $rel" >> "$OUTPUT_FILE"
  emit_file_content "$f" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
  echo "### END FILE: $rel" >> "$OUTPUT_FILE"
  echo "" >> "$OUTPUT_FILE"
  remaining_count=$((remaining_count + 1))
done

# Pula para a próxima linha após a barra de progresso
echo ""
echo -e "${GREEN}✅ $remaining_count arquivos adicionais processados${NC}"

# Tamanho do arquivo gerado
FILE_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
FILE_LINES=$(wc -l < "$OUTPUT_FILE")

echo ""
echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}${BOLD}                      PROCESSO CONCLUÍDO!                       ${NC}"
echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${GREEN}${BOLD}✅ RESUMO DA OPERAÇÃO:${NC}"
echo -e "${GREEN}─────────────────────────────────────────────────${NC}"
echo -e "${GREEN}• Total de arquivos processados:${NC} $FILE_COUNT"
echo -e "${GREEN}• Arquivos prioritários:${NC} $priority_count"
echo -e "${GREEN}• Arquivos adicionais:${NC} $remaining_count"
echo -e "${GREEN}• Tamanho do arquivo gerado:${NC} $FILE_SIZE"
echo -e "${GREEN}• Linhas totais:${NC} $FILE_LINES"
echo ""
echo -e "${YELLOW}${BOLD}📁 ARQUIVO GERADO (RELATIVO):${NC}"
echo -e "${YELLOW}─────────────────────────────────────────────────${NC}"
echo -e "${CYAN}$(get_relative_path "$OUTPUT_FILE")${NC}"
echo ""

# Mostra também o caminho absoluto para referência
echo -e "${BLUE}📌 Caminho absoluto (para referência):${NC}"
echo -e "${CYAN}$OUTPUT_FILE${NC}"
echo ""

# Verifica se o arquivo foi gerado com sucesso
if [ -f "$OUTPUT_FILE" ]; then
  echo -e "${GREEN}${BOLD}✨ Arquivo gerado com sucesso! ✨${NC}"
else
  echo -e "${RED}${BOLD}❌ ERRO: Arquivo não foi gerado!${NC}"
  exit 1
fi

echo ""
echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════════════${NC}"