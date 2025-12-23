# 🚀 Plataforma SaaS Multi-Tenant (Schema por Tenant)

Este projeto é uma **plataforma SaaS multi-tenant** desenvolvida com **Spring Boot**, onde cada cliente (conta) possui **isolamento total de dados através de schemas dedicados no PostgreSQL**.

A arquitetura foi pensada para **segurança, escalabilidade e manutenibilidade**, seguindo práticas utilizadas em sistemas SaaS profissionais.

---

## 📌 Visão Geral

- Modelo **multi-tenant por schema**
- Banco de dados **PostgreSQL**
- **Spring Boot + Hibernate Multitenancy (SCHEMA)**
- Autenticação via **JWT**
- Separação clara entre dados globais (`public`) e dados dos tenants

---

## 🧱 Arquitetura Multi-Tenant

- Cada **Account (cliente)** possui:
  - Um **schema exclusivo** no banco
  - Usuários próprios isolados nesse schema
- O schema `public` armazena:
  - Contas (accounts)
  - Usuários administrativos da plataforma
  - Configurações globais
- O schema do tenant armazena:
  - Usuários do tenant
  - Dados específicos da conta

### 🔄 Resolução de Tenant

- O tenant ativo é armazenado em um **ThreadLocal**
- O `search_path` do PostgreSQL é configurado dinamicamente a cada conexão
- O isolamento é garantido por:
  - `CurrentTenantIdentifierResolver`
  - `SchemaMultiTenantConnectionProvider`

---

## 🔐 Autenticação e Segurança

- Autenticação baseada em **JWT**
- Tokens contêm:
  - ID da conta
  - Schema do tenant
  - Papel do usuário
- Cada requisição:
  - Resolve o tenant correto
  - Configura o schema antes de qualquer operação transacional
- Prevenção de acesso cruzado entre tenants

---

## 👥 Gestão de Usuários

### Usuários da Plataforma (PUBLIC)

- Usuários administrativos globais
- Acesso à administração da plataforma
- Não pertencem a um tenant específico

### Usuários do Tenant

- Usuários isolados por schema
- Funcionalidades:
  - Ativação e desativação
  - Soft delete
  - Reset de senha
  - Controle de tentativas de login
  - Bloqueio temporário

---

## 🏢 Gestão de Contas (Accounts)

Cada conta representa um **cliente do SaaS**.

### Estados da Conta

- `FREE_TRIAL`
- `ACTIVE`
- `SUSPENDED`
- `CANCELLED`

### Regras de Negócio

- Contas suspensas:
  - Usuários do tenant são automaticamente suspensos
- Contas canceladas:
  - Soft delete da conta
  - Soft delete de todos os usuários do tenant
- Contas do sistema:
  - Protegidas contra alterações e exclusões

---

## 🔄 Ciclo de Vida da Conta

### Criação da Conta

1. Criação da conta no schema `public`
2. Criação automática do schema do tenant
3. Migração das tabelas do tenant
4. Criação do administrador da plataforma
5. Criação do administrador do tenant

### Suspensão

- Suspende todos os usuários do tenant
- Dados permanecem preservados

### Cancelamento

- Soft delete da conta
- Soft delete dos usuários do tenant

### Restauração

- Restaura a conta
- Restaura os usuários do tenant

---

## 🔁 Migração e Manutenção de Schemas

- Migrações automáticas por tenant
- Criação de schema idempotente (`IF NOT EXISTS`)
- Verificação de existência de tabelas antes de operações críticas
- Suporte a recuperação de tenants incompletos

---

## 🔧 Transações e Consistência

- Separação clara entre:
  - Operações no schema `public`
  - Operações no schema do tenant
- O tenant é sempre bindado **antes do início da transação**
- Uso de `REQUIRES_NEW` para operações críticas
- Proteção contra vazamento de tenant entre requisições

---

## 📊 Observabilidade e Logs

- Logs detalhados para:
  - Bind e unbind de tenant
  - `search_path` ativo por conexão
  - Início e fim de transações
- Logs ajudam a:
  - Detectar erros de schema
  - Auditar comportamento do sistema
  - Facilitar debug em produção

---

## 🛡️ Segurança e Confiabilidade

- Isolamento físico de dados por schema
- Nenhuma operação de tenant é executada no `public`
- Validações rigorosas antes de ações destrutivas
- Arquitetura preparada para ambientes produtivos

---

## 🎯 Benefícios da Arquitetura

- Escalável para milhares de tenants
- Alto nível de segurança
- Fácil manutenção e evolução
- Aderente a padrões de mercado para SaaS

---

## 📦 Tecnologias Utilizadas

- Java 17+
- Spring Boot
- Spring Security
- Hibernate / JPA
- PostgreSQL
- JWT
- Lombok

---

## 📄 Licença

Este projeto é de uso privado / interno.

---

