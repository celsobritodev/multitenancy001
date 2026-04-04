#!/bin/bash

# ============================================================
# Script: lista-maiores.sh
# Descrição: Lista os 10 maiores arquivos .java do projeto
# Uso: ./scripts/lista-maiores.sh (executar de dentro do diretório scripts)
# ============================================================

# Cores ANSI
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Navegar para o diretório pai (raiz do projeto)
cd ..

# Limpar a tela
clear

# ============================================================
# CABEÇALHO
# ============================================================
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║     📊 TOP 10 MAIORES ARQUIVOS JAVA DO PROJETO                    ║${NC}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================================
# CONTAGEM TOTAL DE ARQUIVOS
# ============================================================
TOTAL_FILES=$(find . -name "*.java" -type f 2>/dev/null | wc -l)
TOTAL_SIZE=$(find . -name "*.java" -type f -exec stat -c %s {} \; 2>/dev/null | awk '{sum+=$1} END {print sum/1024/1024}')
echo -e "${YELLOW}📁 Total de arquivos .java:${NC} ${BOLD}${WHITE}$TOTAL_FILES${NC}"
echo -e "${YELLOW}💾 Tamanho total:${NC} ${BOLD}${WHITE}${TOTAL_SIZE} MB${NC}"
echo ""

# ============================================================
# BARRA DE PROGRESSO DECORATIVA
# ============================================================
echo -e "${CYAN}════════════════════════════════════════════════════════════════════${NC}"
echo ""

# ============================================================
# LISTAGEM DOS 10 MAIORES
# ============================================================
echo -e "${BOLD}${GREEN}🏆 RANKING DOS 10 MAIORES ARQUIVOS${NC}"
echo ""

# Cabeçalho da tabela
echo -e "${BOLD}${BLUE}┌─────┬──────────────────────────────────────────────────┬──────────────┐${NC}"
echo -e "${BOLD}${BLUE}│ ${WHITE}#${BLUE}   │ ${WHITE}ARQUIVO${BLUE}                                                         │ ${WHITE}TAMANHO${BLUE}       │${NC}"
echo -e "${BOLD}${BLUE}├─────┼──────────────────────────────────────────────────┼──────────────┤${NC}"

# Listar os 10 maiores
find . -name "*.java" -type f 2>/dev/null | while read -r file; do
    size=$(ls -lh "$file" 2>/dev/null | awk '{print $5}')
    echo "$size|$file"
done | sort -h -r | head -10 | nl -w1 -s'|' | while IFS='|' read -r rank size filepath; do
    # Remover o "./" do início do caminho
    clean_path=$(echo "$filepath" | sed 's|^\./||')
    
    # Extrair o nome do arquivo (último componente do path)
    filename=$(basename "$clean_path")
    
    # Extrair o diretório
    dirpath=$(dirname "$clean_path")
    
    # Cor do rank
    case $rank in
        1) RANK_COLOR="${BOLD}${RED}🥇${NC}";;
        2) RANK_COLOR="${BOLD}${YELLOW}🥈${NC}";;
        3) RANK_COLOR="${BOLD}${MAGENTA}🥉${NC}";;
        *) RANK_COLOR="${BOLD}${WHITE}$rank${NC}";;
    esac
    
    # Cor do tamanho baseado no tamanho
    if [[ $size == *"M"* ]]; then
        SIZE_COLOR="${RED}${BOLD}"
    elif [[ $size == *"K"* ]] && [[ ${size%K} -gt 50 ]]; then
        SIZE_COLOR="${YELLOW}${BOLD}"
    else
        SIZE_COLOR="${GREEN}"
    fi
    
    # Formatar a linha da tabela
    printf "${BLUE}│ ${RANK_COLOR} ${BLUE} │ ${WHITE}%-48s ${BLUE}│ ${SIZE_COLOR}%10s ${BLUE}│${NC}\n" "$filename" "$size"
    
    # Mostrar o caminho completo na linha seguinte (com indentação)
    printf "${BLUE}│     │ ${CYAN}📁 %-46s ${BLUE}│              │${NC}\n" "$dirpath"
    
    # Linha separadora (exceto após o último)
    if [ $rank -lt 10 ]; then
        echo -e "${BLUE}├─────┼──────────────────────────────────────────────────┼──────────────┤${NC}"
    fi
done

# Rodapé da tabela
echo -e "${BOLD}${BLUE}└─────┴──────────────────────────────────────────────────┴──────────────┘${NC}"
echo ""

# ============================================================
# ESTATÍSTICAS ADICIONAIS
# ============================================================
echo -e "${CYAN}════════════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${GREEN}📈 ESTATÍSTICAS RÁPIDAS${NC}"
echo ""

# Calcular média de linhas (aproximada)
AVG_SIZE=$(find . -name "*.java" -type f -exec stat -c %s {} \; 2>/dev/null | awk '{sum+=$1; count++} END {if(count>0) printf "%.0f", sum/count/1024; else print "0"}')
echo -e "${YELLOW}📏 Tamanho médio por arquivo:${NC} ${BOLD}${WHITE}${AVG_SIZE} KB${NC}"

# Encontrar o maior tamanho em MB
MAX_SIZE=$(find . -name "*.java" -type f -exec stat -c %s {} \; 2>/dev/null | sort -rn | head -1 | awk '{print $1/1024/1024}')
echo -e "${YELLOW}🏆 Maior arquivo:${NC} ${BOLD}${WHITE}${MAX_SIZE} MB${NC}"

echo ""
echo -e "${BOLD}${GREEN}✅ Análise concluída!${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════════════${NC}"