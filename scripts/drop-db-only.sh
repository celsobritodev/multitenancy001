#!/usr/bin/env bash
set -e

# =========================================================
# DROP DATABASE ONLY - PostgreSQL (AUTO MODE - NO CONFIRMATION)
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
echo -e "${PURPLE}           DROP DATABASE ONLY - PostgreSQL (AUTO MODE)         ${NC}"
echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"
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

# Verificar se aplicação está rodando
echo -e "${CYAN}🛑 Verificando se aplicação está rodando na porta 8080...${NC}"
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Aplicação encontrada na porta 8080${NC}"
    echo -e "${CYAN}   Parando aplicação automaticamente...${NC}"
    curl -X POST http://localhost:8080/actuator/shutdown > /dev/null 2>&1
    echo -e "${GREEN}   ✅ Aplicação parada${NC}"
    sleep 3
else
    echo -e "${GREEN}✅ Nenhuma aplicação rodando na porta 8080${NC}"
fi
echo ""

# Verificar se o banco existe antes de tentar dropar
echo -e "${CYAN}🔍 Verificando se o banco $DB_NAME existe...${NC}"
DB_EXISTS=$(psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'")
if [ "$DB_EXISTS" != "1" ]; then
    echo -e "${YELLOW}⚠️  Banco $DB_NAME não existe. Nada a fazer.${NC}"
    exit 0
fi
echo -e "${GREEN}✅ Banco $DB_NAME encontrado${NC}"
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

# Mostrar resultado
echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✨  BANCO REMOVIDO COM SUCESSO!${NC}"
echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${CYAN}📊 Status do banco:${NC}"
echo -e "   🆔 Nome:     ${YELLOW}$DB_NAME${NC}"
echo -e "   📦 Status:   ${YELLOW}Removido${NC}"
echo ""
echo -e "${CYAN}🚀 Próximos passos:${NC}"
echo -e "   1. ${YELLOW}Para recriar o banco, execute:${NC}"
echo -e "      createdb -U $DB_USER $DB_NAME"
echo -e "      ou"
echo -e "      psql -U $DB_USER -c \"CREATE DATABASE $DB_NAME;\""
echo -e ""
echo -e "   2. ${YELLOW}Inicie a aplicação:${NC}"
echo -e "      ./mvnw spring-boot:run"
echo -e ""
echo -e "   3. ${YELLOW}Execute as migrações:${NC} (automáticas)"
echo -e "      As migrações Flyway rodarão ao iniciar a aplicação"
echo ""
echo -e "${PURPLE}═══════════════════════════════════════════════════════════════${NC}"