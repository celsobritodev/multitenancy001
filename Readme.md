# Arquitetura — multitenancy001 (Spring Boot + PostgreSQL, multi-tenant por schema)

## Visão geral
O sistema é um **SaaS multi-tenant** com dois “mundos” principais:

1. **Platform / Public (controle da plataforma)**  
   - Tudo que pertence à plataforma (cadastro de contas, usuários da plataforma, planos, pagamentos).
   - Vive no **schema `public`** do Postgres.

2. **Tenant (dados do cliente)**  
   - Tudo que pertence ao cliente (usuários do tenant e domínio do negócio: categorias, produtos, etc.).
   - Vive em **schemas separados por conta** (ex.: `tenant_foton`, `tenant_xxx`).

A aplicação é **uma só**, mas o Hibernate troca o schema ativo em runtime conforme o tenant.

---

## Componentes principais

### 1) Banco de dados (PostgreSQL)
- **Schema `public` (plataforma)**
  - `accounts` → cadastro de contas/tenants (inclui `schema_name`, `slug`, status/plano etc.)
  - `users_account` → usuários administrativos vinculados a uma conta (ex.: superadmin)
  - `accounts_users_permissions` → permissões específicas de usuários da plataforma

- **Schema do tenant (por conta)**
  - `users_tenant` → usuários do tenant (operadores/usuários do cliente)
  - `user_tenant_permissions` → permissões por usuário do tenant
  - `categories`, `subcategories`, `products` (e outros domínios do tenant)

---

## Migrações (Flyway)
O projeto separa migrações por contexto:

- `db/migration/accounts/*`
  - Roda no schema `public` (estrutura da plataforma)
  - Ex.: `V1__create_accounts.sql`, `V2__create_accounts_users.sql`, inserts do platform/superadmin

- `db/migration/tenants/*`
  - Roda em **cada schema de tenant**
  - Ex.: `V1__create_tenants_users.sql`, `V3__create_categories.sql`, etc.

### Fluxo típico
1. Ao subir a aplicação: garante que o **public** está migrado.
2. Ao criar uma conta:
   - cria o schema do tenant (`schema_name`)
   - aplica migrações de tenant naquele schema

---

## Multi-tenancy (Hibernate SCHEMA)
O multi-tenant é por **schema** (não por banco e não por tabela compartilhada).

### Peças
- `TenantContext`
  - guarda o tenant atual (ex.: `public` ou `tenant_xxx`) normalmente via ThreadLocal.

- `CurrentTenantIdentifierResolverImpl`
  - informa ao Hibernate **qual schema** usar no momento.

- `SchemaMultiTenantConnectionProvider`
  - fornece conexão e define o schema no PostgreSQL (`SET search_path`) ou equivalente.

- `HibernateMultitenancyConfig`
  - configura o `EntityManagerFactory` com:
    - `hibernate.multi_tenant = SCHEMA`
    - connection provider e resolver

---

## Camada de domínio / entidades
O projeto separa entidades por “mundo”:

### Platform (schema `public`)
- `platform.domain.tenant.TenantAccount` → mapeia `accounts`
- `platform.domain.user.PlatformUser` → mapeia `users_account`
- `platform.domain.billing.Payment` → mapeia dados de billing

### Tenant (schema do cliente)
- `entities.tenant.Category`, `Subcategory`, `Product`, `Sale`, `TenantUser`, etc.

---

## Repositories
- Repositories de platform acessam **schema `public`**.
- Repositories de tenant acessam o **schema resolvido no runtime**.

Exemplo importante (PlatformUser):
- `PlatformUser` tem `account` (ManyToOne) e não `accountId` no Java
- Logo, métodos derivados devem usar property path:
  - `findByAccount_Id(...)`, `countByAccount_IdAndDeletedFalse(...)`

---

## Segurança (JWT)
A autenticação é feita via **JWT**, com filtro:

- `JwtAuthenticationFilter`
  - intercepta requisições
  - valida token
  - carrega usuário (`CustomUserDetailsService`)
  - injeta autenticação no contexto do Spring Security

Existem endpoints distintos para:
- autenticação/admin (plataforma)
- autenticação/tenant (cliente)

---

## Controllers (API)
Principais controladores:
- `AdminAccountsController` → gestão de contas (platform/admin)
- `PlatformUsersAdminController` → usuários da plataforma (ex.: super admin)
- `SignupController` → criação de contas / onboarding
- `AdminAuthController` e `TenantAuthController` → login/token por contexto
- `UserTenantController`, `ProductController` → operações dentro do tenant

---

## Services (regras e orquestração)
- `AccountService` / `TenantAccountService` → criação/gestão de contas e schemas
- `TenantSchemaService` / `TenantMigrationService` → criação de schema + migração tenant
- `TenantUserService` → usuários do tenant
- `PlatformUserService` → usuários da plataforma
- `ProductService` → domínio de produto no tenant
- `UsernameGeneratorService` / `UsernameUniquenessService` → geração e validação de usernames

---

## Fluxos essenciais

### 1) Criação de conta (tenant)
1. Request chega no endpoint de signup
2. Cria registro em `public.accounts`
3. Cria schema do tenant (`schema_name`)
4. Executa migrações `db/migration/tenants` nesse schema
5. (Opcional) cria usuário inicial do tenant

### 2) Request comum (tenant)
1. Filtro/Interceptor determina tenant (header/host/subdomínio)
2. `TenantContext` é setado com `schema_name`
3. Hibernate usa schema correto automaticamente
4. Repositories/Services operam no schema do tenant

---

## Padrões e decisões
- Multi-tenant por schema (isolamento forte por cliente)
- Public schema para metadados/controle de plataforma
- Flyway separando migrações de plataforma e tenant
- JWT para autenticação, com controllers e serviços por contexto
- Soft delete em várias tabelas (`deleted`, `deleted_at`)

---
