#!/usr/bin/env bash
set -e

# Limpar a tela
clear

# =========================================================
# DROP AND CREATE DATABASE - PostgreSQL (AUTO MODE - NO CONFIRMATION)
# =========================================================

# Cores para output (funciona no Git Bash)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${PURPLE}        DROP AND CREATE DATABASE - PostgreSQL (AUTO MODE)      ${NC}"
echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

# Mostrar de onde o script está sendo executado
echo -e "${CYAN}📂 Script executado de:${NC}"
echo -e "   ${YELLOW}$(pwd)${NC}"
echo -e "${CYAN}📂 Script localizado em:${NC}"
echo -e "   ${YELLOW}$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)${NC}"
echo ""

# Configurações do banco (usando suas credenciais padrão)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-admin}"
DB_NAME="${DB_NAME:-db_multitenancy}"

# No Windows/Git Bash, precisamos configurar o PATH do PostgreSQL
export PATH=$PATH:"/c/Program Files/PostgreSQL/17/bin"
export PGHOST="${DB_HOST}"
export PGPORT="${DB_PORT}"
export PGUSER="${DB_USER}"
export PGPASSWORD="${DB_PASSWORD}"

echo -e "${CYAN}🔌 Configurações de conexão:${NC}"
echo -e "   📍 Host:     ${YELLOW}$DB_HOST${NC}"
echo -e "   🔢 Porta:    ${YELLOW}$DB_PORT${NC}"
echo -e "   👤 Usuário:  ${YELLOW}$DB_USER${NC}"
echo -e "   📊 Banco:    ${YELLOW}$DB_NAME${NC}"
echo ""

# Verificar se psql está disponível
echo -e "${CYAN}🔍 Verificando se PostgreSQL Client (psql) está disponível...${NC}"
if ! command -v psql &> /dev/null; then
    echo -e "${RED}❌ Erro: psql não encontrado${NC}"
    echo -e "${YELLOW}   Verifique se o PostgreSQL Client está instalado e no PATH${NC}"
    echo -e "${YELLOW}   Caminho comum: C:\\Program Files\\PostgreSQL\\17\\bin${NC}"
    exit 1
fi
echo -e "${GREEN}✅ psql encontrado${NC}"
echo ""

# Testar conexão
echo -e "${CYAN}🔍 Testando conexão com PostgreSQL...${NC}"
if psql -d postgres -c "SELECT 1" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Conexão estabelecida com PostgreSQL${NC}"
else
    echo -e "${RED}❌ Erro: Não foi possível conectar ao PostgreSQL${NC}"
    echo -e "${YELLOW}   Verifique se:${NC}"
    echo -e "${YELLOW}   1. O PostgreSQL está rodando${NC}"
    echo -e "${YELLOW}   2. As credenciais estão corretas (usuário: postgres, senha: admin)${NC}"
    echo -e "${YELLOW}   3. O PostgreSQL está na porta 5432${NC}"
    exit 1
fi
echo ""

# Verificar se aplicação está rodando na porta 8080 (versão melhorada)
echo -e "${CYAN}🛑 Verificando se aplicação está rodando na porta 8080...${NC}"

# Método 1: Verificar via netstat (Windows)
PID_8080=$(netstat -ano | grep ":8080" | grep LISTENING | awk '{print $5}' | cut -d':' -f2 | head -1)

# Método 2: Verificar via curl (fallback)
if [ -z "$PID_8080" ] && command -v curl &> /dev/null; then
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  Aplicação encontrada na porta 8080 (via actuator)${NC}"
        echo -e "${CYAN}   Parando aplicação automaticamente...${NC}"
        curl -X POST http://localhost:8080/actuator/shutdown > /dev/null 2>&1
        echo -e "${GREEN}   ✅ Aplicação parada${NC}"
        sleep 3
        PID_8080="found"
    fi
fi

# Se encontrou PID via netstat, matar o processo
if [ -n "$PID_8080" ] && [ "$PID_8080" != "found" ]; then
    echo -e "${YELLOW}⚠️  Processo encontrado na porta 8080 (PID: $PID_8080)${NC}"
    echo -e "${CYAN}   Matando processo...${NC}"
    
    # Matar o processo
    taskkill //PID $PID_8080 //F > /dev/null 2>&1
    
    # Verificar se matou
    sleep 2
    if netstat -ano | grep ":8080" | grep LISTENING > /dev/null 2>&1; then
        echo -e "${RED}❌ Falha ao matar processo na porta 8080${NC}"
        exit 1
    else
        echo -e "${GREEN}✅ Processo na porta 8080 finalizado${NC}"
    fi
elif [ -z "$PID_8080" ]; then
    echo -e "${GREEN}✅ Nenhuma aplicação rodando na porta 8080${NC}"
fi
echo ""

# Finalizar conexões com o banco
echo -e "${CYAN}🔚 Finalizando conexões ativas com o banco...${NC}"
psql -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME';" > /dev/null 2>&1
echo -e "${GREEN}✅ Conexões finalizadas${NC}"
echo ""

# AVISO (apenas informativo, sem confirmação)
echo -e "${RED}🗑️  DROPPANDO banco $DB_NAME...${NC}"
echo -e "${RED}   ⚠️  ATENÇÃO: TODOS OS DADOS SERÃO PERDIDOS!${NC}"
echo -e "${YELLOW}   - Dados de tenants${NC}"
echo -e "${YELLOW}   - Usuários do Control Plane${NC}"
echo -e "${YELLOW}   - Configurações${NC}"
echo -e "${YELLOW}   - Logs de auditoria${NC}"
echo -e "${CYAN}   🚀 Modo automático - prosseguindo sem confirmação...${NC}"
echo ""

# Dropar banco
echo -e "${CYAN}🗑️  Removendo banco de dados...${NC}"
if psql -d postgres -c "DROP DATABASE IF EXISTS \"$DB_NAME\";" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Banco $DB_NAME removido com sucesso${NC}"
else
    echo -e "${RED}❌ Erro ao remover banco${NC}"
    exit 1
fi
echo ""

# Criar banco
echo -e "${CYAN}🆕 Criando novo banco de dados...${NC}"
if psql -d postgres -c "CREATE DATABASE \"$DB_NAME\";" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Banco $DB_NAME criado com sucesso${NC}"
else
    echo -e "${RED}❌ Erro ao criar banco${NC}"
    exit 1
fi
echo ""

# Mostrar resultado
echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✨  BANCO RECRIADO COM SUCESSO!${NC}"
echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${CYAN}📊 Status do banco:${NC}"
echo -e "   🆔 Nome:     ${YELLOW}$DB_NAME${NC}"
echo -e "   📦 Tamanho:  ${YELLOW}Novo/vazio${NC}"
echo ""

# Salvar o diretório atual (scripts)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Voltar para o diretório raiz do projeto
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo -e "${CYAN}🚀 Próximos passos:${NC}"
echo -e "   1. ${YELLOW}Inicie a aplicação:${NC} (iniciando automaticamente...)"
echo -e "      ./mvnw spring-boot:run"
echo -e ""
echo -e "   2. ${YELLOW}Execute as migrações:${NC} (automáticas)"
echo -e "      As migrações Flyway rodarão ao iniciar a aplicação"
echo -e ""
echo -e "   3. ${YELLOW}Execute os testes E2E:${NC}"
echo -e "      newman run e2e/multitenancy001.postman_collection.v3.0-bootstrap-tenant-auth-controlplane-auth.json -e e2e/multitenancy001.local.postman_environment.v3.0.json"
echo -e "      newman run e2e/multitenancy001.postman_collection.v4.0-controlplane-users.json -e e2e/multitenancy001.local.postman_environment.v4.0.json"
echo -e "      newman run e2e/multitenancy001.postman_collection.v5.0-controlplane-users-complete.json -e e2e/multitenancy001.local.postman_environment.v5.0.json"
echo ""

echo -e "${CYAN}📂 Diretório do projeto: ${YELLOW}$PROJECT_ROOT${NC}"
echo -e "${CYAN}🚀 Iniciando aplicação Spring Boot...${NC}"
echo -e "${YELLOW}   Pressione Ctrl+C para parar a aplicação quando necessário${NC}"
echo ""

# Verificar se o mvnw existe
if [ ! -f "./mvnw" ]; then
    echo -e "${RED}❌ Erro: ./mvnw não encontrado em $PROJECT_ROOT${NC}"
    echo -e "${YELLOW}   Arquivos no diretório atual:${NC}"
    ls -la
    exit 1
fi

# Executar a aplicação
./mvnw spring-boot:run

# Nota: O script vai parar aqui enquanto a aplicação estiver rodando
# Quando a aplicação for interrompida (Ctrl+C), o script continua

echo ""
echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✨  APLICAÇÃO FINALIZADA${NC}"
echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"