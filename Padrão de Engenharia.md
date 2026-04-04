# 📘 HANDOFF TÉCNICO — PADRÃO COMPLETO DE ENGENHARIA, ARQUITETURA E EXECUÇÃO

---

# 🚀 1. PRINCÍPIO FUNDAMENTAL

> **Sempre preferir a solução correta e completa, mesmo que envolva refatoração ampla, ao invés de ajustes cirúrgicos.**

Este projeto NÃO aceita:
- patches temporários
- soluções paliativas
- “funciona por enquanto”

---

# 🧠 2. FILOSOFIA DE DESENVOLVIMENTO

## 🔧 2.1 Refatoração como regra

✔ Sempre:
- refatoração completa
- reorganização estrutural
- correção na raiz do problema

❌ Nunca:
- ajustes locais mantendo problema estrutural
- duplicação de lógica
- hacks

---

## 📌 2.2 Regra de ouro

> **Se precisar mexer em 10 arquivos para fazer certo — mexa nos 10.**

---

## 🧱 2.3 Filosofia prática

- Código é tratado como **arquitetura viva**
- Testes E2E são tratados como **validação arquitetural**
- Services representam **casos de uso reais**, não wrappers

---

# 🏗️ 3. ARQUITETURA

## 🧱 3.1 Padrão obrigatório

- DDD + Layered (simples)
- ❌ Sem ports & adapters

---

## 📚 3.2 Camadas

### Controller
- recebe request
- valida entrada básica
- chama service

### Application Service
- orquestra caso de uso
- NÃO contém regra de domínio pura

### Domain
- regras de negócio
- entidades ricas
- invariantes

### Repository
- persistência apenas
- sem regra de negócio

---

## 🔒 3.3 Boundary (CRÍTICO)

Separação obrigatória:

- CONTROL PLANE (public schema)
- TENANT (schema por conta)

❌ PROIBIDO:
- crossing direto

✔ CORRETO:
- IntegrationService
- UnitOfWork
- Executor

---

## 🧩 3.4 Regras de crossing

- crossing deve ser explícito
- nunca implícito
- sempre auditável

---

# 🧩 4. ESTRUTURA DE SERVIÇOS

## Tipos padrão

| Tipo | Uso |
|------|-----|
| CommandService | Escrita |
| QueryService | Leitura |
| LifecycleService | Estado |
| OrchestrationService | Fluxo |
| AuditService | Auditoria |
| ValidationService | Validação |
| Resolver | Resolução |
| Support | Auxiliar |

---

## 🪶 Fachada fina

Classe principal:
- delega
- não contém lógica pesada
- mantém compatibilidade

---

## ❌ Nomes proibidos

- GenericService
- HelperService
- ManagerService

---

# 💻 5. PADRÃO DE CÓDIGO

## 📦 5.1 Código completo

✔ Sempre:
- classe completa
- compilável
- com imports
- com Javadoc
- com logs

❌ Nunca:
- snippet
- pseudo código

---

## 📝 5.2 Javadoc obrigatório

```java
/**
 * Serviço responsável por X.
 *
 * Regras:
 * - regra 1
 * - regra 2
 *
 * Fluxo:
 * - passo 1
 * - passo 2
 */