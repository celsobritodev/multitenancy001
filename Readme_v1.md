# 🧩 Multitenancy SaaS Platform – Architecture Overview

Este projeto implementa uma **arquitetura SaaS multitenant com isolamento por schema**, utilizando **Spring Boot**, **Spring Security**, **JWT**, **Flyway** e **PostgreSQL**.

O sistema separa claramente:
- **Gestão da plataforma (Super Admin)**
- **Gestão de cada tenant (Admin do tenant e usuários internos)**

---

## 🏗️ Visão Geral da Arquitetura

A arquitetura é baseada em **dois níveis de usuários** e **dois contextos de dados**:

### 🔹 1. Contexto PLATFORM (schema `public`)
Responsável por:
- Gerenciar contas (tenants)
- Autenticar e autorizar usuários da plataforma
- Controlar status, planos, limites e ciclo de vida das contas

### 🔹 2. Contexto TENANT (schema dinâmico por conta)
Responsável por:
- Usuários finais da conta
- Papéis (roles) e permissões internas
- Dados isolados por tenant

Cada conta possui **seu próprio schema no banco**.

---

## 🗄️ Estrutura de Banco de Dados

### 📌 Schema `public` (PLATFORM)

#### `accounts`
Tabela central que representa cada tenant do sistema.

Principais campos:
- `id`
- `name`
- `slug` (identificador público do tenant)
- `schema_name` (schema do banco)
- `status` (FREE_TRIAL, ACTIVE, SUSPENDED, CANCELLED)
- `max_users`, `max_products`, etc.
- `is_system_account` (ex: conta da plataforma)

#### `users_account`
Usuários da **plataforma**, não pertencem a um tenant.

Roles disponíveis:
- `SUPER_ADMIN`
- `SUPPORT`
- `STAFF`

👉 Esses usuários:
- Logam via `/api/admin/auth/login`
- Gerenciam todas as contas
- Nunca acessam dados de tenant diretamente

---

### 📌 Schema do TENANT (ex: `tenant_empresa_xxx`)

Criado dinamicamente para cada conta.

#### `users_tenant`
Usuários internos da conta.

Principais campos:
- `account_id`
- `username`
- `email`
- `password`
- `role`
- `active`
- `deleted`

Roles disponíveis:
- `TENANT_ADMIN`
- `MANAGER`
- `VIEWER`
- `USER`

#### `user_tenant_permissions`
Permissões específicas atribuídas a cada usuário do tenant.

Relacionamento:
- `user_tenant_id`
- `permission`

---

## 🔐 Modelo de Autenticação

### 🟣 Plataforma (Super Admin)

- Endpoint: `/api/admin/auth/login`
- Autenticação sempre no schema `public`
- Token JWT com:
  - `type = ACCOUNT`
  - `roles = ROLE_SUPER_ADMIN | ROLE_SUPPORT | ROLE_STAFF`
  - `accountId`
  - `tenantSchema = public`

### 🔵 Tenant (Usuários da Conta)

- Endpoint: `/api/auth/login`
- Fluxo:
  1. Resolve a conta via `slug` no `public`
  2. Valida status da conta
  3. Binda o `TenantContext`
  4. Autentica no schema do tenant

- Token JWT com:
  - `type = TENANT`
  - `roles = ROLE_TENANT_ADMIN | ROLE_MANAGER | ...`
  - `accountId`
  - `tenantSchema`

---

## 🔄 Contexto de Tenant (`TenantContext`)

O projeto usa um **TenantContext baseado em ThreadLocal**, que define dinamicamente o schema ativo.

### Regras importantes:
- Sempre **unbind** antes de acessar o `public`
- Sempre **bind** antes de acessar dados do tenant
- Nunca misturar operações de schemas na mesma transação

---

## 🚀 Criação de uma Conta (Tenant Lifecycle)

Fluxo completo ao criar uma nova conta:

1. **PUBLIC**
   - Cria registro em `accounts`
2. **BANCO**
   - Cria schema do tenant
3. **FLYWAY**
   - Executa migrations do tenant
4. **TENANT**
   - Cria automaticamente um usuário `TENANT_ADMIN`
5. Conta entra em `FREE_TRIAL`

---

## 🧑‍💼 Responsabilidades por Papel

### SUPER_ADMIN (Platform)
- Criar, suspender, cancelar contas
- Gerenciar planos, limites e pagamentos
- Listar usuários de qualquer tenant
- Restaurar contas e usuários

### TENANT_ADMIN (Tenant)
- Gerenciar usuários do tenant
- Criar, editar e remover usuários
- Definir roles e permissões
- Administrar dados da própria conta

### Outros roles do tenant
- Acesso restrito conforme permissões
- Sem visibilidade de outros tenants

---

## 🧬 Migrations com Flyway

### Platform
- Executadas no schema `public`
- Criam `accounts` e `users_account`
- Inserem conta da plataforma e `SUPER_ADMIN`

### Tenant
- Executadas por schema
- Criam `users_tenant` e `user_tenant_permissions`
- Totalmente isoladas por tenant

> Em ambiente de desenvolvimento, o banco pode ser dropado sem impacto.
> Em produção, migrations são incrementais.

---

## ✅ Principais Benefícios da Arquitetura

- 🔐 Isolamento total de dados por tenant
- 🧱 Separação clara entre plataforma e clientes
- 📈 Escalável para milhares de tenants
- 🔄 Fácil controle de ciclo de vida da conta
- 🧠 Modelo alinhado com SaaS comerciais reais

---

## 📌 Observação Final

Este projeto segue boas práticas de:
- Multi-tenancy por schema
- Segurança com JWT
- Separação de responsabilidades
- Evolução futura para billing, métricas e auditoria

