# 📘 Roadmap de Suítes E2E — multitenancy001

## 🔷 Contexto Atual

Projeto: **multitenancy001**
Arquitetura: **DDD / layered (sem ports & adapters)**
Multi-tenancy: **schema por tenant + control plane (public)**
Execução de testes: **Newman + Git Bash + suites versionadas (VXX)**

### ✅ Estado atual

A suíte **V32.9.1** representa o baseline de produção:

* Fluxos principais estabilizados
* Multi-tenant validado
* Inventory + Sales consistente
* Hard limits funcionando
* Execução determinística

👉 A partir daqui não é mais correção — é **expansão de cobertura**

---

# 🚀 ROADMAP DE EVOLUÇÃO DAS SUÍTES

## 🔥 V33 — Control Plane Accounts (Admin & Lifecycle)

### Objetivo

Cobrir completamente o ciclo de vida das contas no control plane.

### Cobertura

* Consulta de contas
* Detalhes administrativos
* Contagem por status
* Mudança de status
* Efeitos colaterais (side effects)
* Provisioning events

### Risco coberto

* Conta ativa/inativa inconsistente
* Tenant operando com status inválido

---

## 🔥 V34 — Control Plane Users (Gestão Completa)

### Objetivo

Cobrir todo o gerenciamento de usuários administrativos.

### Cobertura

* Criar usuário
* Editar usuário
* Reset de senha
* Troca de senha própria
* Suspensão / reativação
* Atualização de permissões
* Proteção de usuários built-in

### Risco coberto

* Escalada de privilégio
* Acesso indevido

---

## 🔥 V35 — Security & Audit

### Objetivo

Validar trilhas de auditoria e segurança.

### Cobertura

* Auth events
* Security audit events
* Login/logout/refresh auditados
* Mudança de senha auditada
* Ações administrativas auditadas
* Access denied / authentication entry
* Spoofing de tenant

### Risco coberto

* Falta de rastreabilidade
* Falha de compliance

---

## 🔥 V36 — Tenant Password & Recovery

### Objetivo

Cobrir ciclo completo de credenciais do tenant.

### Cobertura

* Forgot password
* Reset password
* Must change password
* Invalidação de sessão
* Challenge expiration

### Risco coberto

* Falha de segurança em recuperação de acesso

---

## 🔥 V37 — Tenant Users Advanced Permissions

### Objetivo

Validar RBAC completo no tenant.

### Cobertura

* Criação com papéis
* Permissões explícitas
* Tentativas de acesso negado
* Escopo de permission
* Suspensão com impacto real

### Risco coberto

* Quebra de isolamento de permissões

---

## 🔥 V38 — Provisioning & Tenant Readiness

### Objetivo

Cobrir criação e readiness de tenants.

### Cobertura

* Signup completo
* Criação de schema
* Readiness
* Falha de provisionamento
* Retry / reprovision
* Required tables

### Risco coberto

* Tenant inconsistente
* Ambiente parcialmente provisionado

---

## 🔥 V39 — Scheduling & Jobs

### Objetivo

Cobrir comportamento operacional (jobs).

### Cobertura

* Criação de schedules
* Consulta
* Execução
* Impacto de status da conta

### Risco coberto

* Jobs executando indevidamente

---

## 🔥 V40 — Debug & Observability

### Objetivo

Validar integridade do TenantContext.

### Cobertura

* Endpoint debug
* Schema correto
* Contexto public vs tenant
* Isolamento entre tenants

### Risco coberto

* Vazamento de contexto
* Corrupção de tenant

---

## 🔥 V41 — Bootstrap / Flyway / Disaster Recovery

### Objetivo

Garantir boot limpo e recuperação.

### Cobertura

* DB vazio → boot
* Migrations completas
* Built-in users
* Falhas controladas

### Risco coberto

* Sistema não subir em produção

---

## 🔥 V42 — Sales & Inventory Advanced

### Objetivo

Cobrir regras avançadas de domínio.

### Cobertura

* Reservation / release
* Returns
* Movements completos
* Ledger consistency
* Drift detection

### Risco coberto

* Inconsistência financeira/estoque

---

# 📊 PRIORIZAÇÃO

## Alta prioridade

* V33
* V34
* V35
* V38

## Média prioridade

* V36
* V37
* V39
* V40

## Baixa prioridade (refinamento)

* V41
* V42

---

# 🧠 REGRA FUNDAMENTAL

```
V(N+1) = V(N) + novos testes
NUNCA remover cobertura
NUNCA regredir
```

---

# 🏁 CONCLUSÃO

Com a V32.9.1 você atingiu:

* Base transacional completa
* Sistema determinístico
* Testes confiáveis

As próximas versões elevam o projeto para:

👉 **nível enterprise completo (admin + security + operação + lifecycle)**

---

# 📦 PADRÃO DE ENTREGA

Sempre manter:

* pasta: `e2e/teste_vXX/`
* STRICT + ULTRA
* scripts `.sh`
* logs
* execução via Git Bash
* entrega em `.zip`

---

**Fim do documento**
