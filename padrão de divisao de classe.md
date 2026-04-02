# 🚀 PADRÃO OFICIAL — DIVISÃO DE CLASSES (DDD + LAYERED)

## 📌 OBJETIVO

Definir um padrão **único, consistente e obrigatório** para dividir classes grandes (God Services)
no projeto.

Este padrão deve ser seguido SEMPRE que uma classe crescer ou ficar com múltiplas responsabilidades.

---

# 🧱 PRINCÍPIO BASE

Toda divisão deve responder:

1. Quem **recebe** a requisição?
2. Quem **coordena** o fluxo?
3. Quem **executa responsabilidade específica**?
4. Quem **apoia (resolver, validator, factory, mapper)**?

---

# 🧭 ESTRUTURA PADRÃO DE DIVISÃO

## 🔹 1. CommandService (WRITE)

### ✔ Quando usar:

* Recebe requisição do Controller
* Executa operações de escrita (create, update, delete)
* Valida entrada básica
* Delega para orchestration ou serviços internos

### 📛 Naming:

<Contexto><Assunto>CommandService

### 💡 Ex:

* ControlPlanePaymentCommandService
* TenantUserCommandService

---

## 🔹 2. QueryService (READ)

### ✔ Quando usar:

* Apenas leitura
* Não altera estado
* Monta respostas de consulta

### 📛 Naming:

<Contexto><Assunto>QueryService

### 💡 Ex:

* ControlPlanePaymentQueryService
* TenantInventoryQueryService

---

## 🔹 3. OrchestrationService (FLUXO COMPLEXO)

### ✔ Quando usar:

* Coordena múltiplos passos
* Decide fluxo (if/else de negócio)
* Chama vários services

### 📛 Naming:

<Contexto><CasoDeUso>OrchestrationService

### 💡 Ex:

* ControlPlaneAccountPlanChangeOrchestrationService
* TenantPlanChangeOrchestrationService

### ❗ REGRA:

Orquestrador **não executa tudo**, apenas coordena.

---

## 🔹 4. LifecycleService (ESTADO)

### ✔ Quando usar:

* Controla ciclo de vida da entidade
* Transições de estado
* Invariantes de domínio

### 📛 Naming:

<Contexto><Entidade>LifecycleService

### 💡 Ex:

* ControlPlanePaymentLifecycleService

---

## 🔹 5. ExecutionService (AÇÃO PONTUAL)

### ✔ Quando usar:

* Executa uma operação específica
* Não orquestra fluxo completo

### 📛 Naming:

<Contexto><Acao>ExecutionService

### 💡 Ex:

* PlanUpgradeExecutionService

---

## 🔹 6. Validator / ValidationService

### ✔ Quando usar:

* Validar regras
* Não executar fluxo completo

### 📛 Naming:

* Simples: <Assunto>Validator
* Complexo: <Assunto>ValidationService

### 💡 Ex:

* PlanChangeRequestValidator
* PaymentValidationService

---

## 🔹 7. Resolver

### ✔ Quando usar:

* Resolver dados/contexto
* Buscar informações necessárias

### 📛 Naming:

<Contexto><Assunto>Resolver

### 💡 Ex:

* TenantSubscriptionAccountResolver
* AccountUsageResolver

---

## 🔹 8. Factory

### ✔ Quando usar:

* Construção padronizada de objetos
* Evitar lógica de criação espalhada

### 📛 Naming:

<Contexto><Assunto>Factory

### 💡 Ex:

* PlanUpgradeIdempotencyKeyFactory
* PaymentDescriptionFactory

---

## 🔹 9. Mapper

### ✔ Quando usar:

* Converter Domain ↔ DTO
* Transformar dados

### 📛 Naming:

<Contexto><Assunto>Mapper

### 💡 Ex:

* AccountApiMapper
* PaymentResponseMapper

---

## 🔹 10. IntegrationService

### ✔ Quando usar:

* Cruzar boundary (PUBLIC ↔ TENANT)
* Integração externa (ex: Mercado Livre)

### 📛 Naming:

<Contexto><Assunto>IntegrationService

### 💡 Ex:

* TenantUsersIntegrationService
* MercadoLivreOrderIntegrationService

---

# 🧱 PADRÃO COMPLETO DE UM MÓDULO

Para um domínio importante:

* CommandService
* QueryService
* OrchestrationService
* LifecycleService (se necessário)
* Validator
* Resolver
* Factory
* Mapper
* IntegrationService (se cruzar boundary)

---

# ❌ NOMES PROIBIDOS

Nunca usar:

* ManagerService
* ProcessorService
* HelperService
* UtilService
* CommonService
* GenericService

👉 Não dizem NADA sobre responsabilidade

---

# ⚠️ QUANDO QUEBRAR UMA CLASSE

Quebrar quando houver:

* mistura de READ + WRITE
* muitos if/else de negócio
* acesso a vários repositories
* validação + execução + mapping juntos
* nome genérico
* Javadoc com múltiplas responsabilidades

---

# 🧠 REGRA DE OURO

"Uma classe deve ter UMA responsabilidade clara e um nome que explique exatamente o que ela faz."

---

# 🧪 EXEMPLO PRÁTICO

## ❌ ERRADO (God Service)

ControlPlanePaymentService

Faz:

* create
* validate
* status
* upgrade
* queue
* mapping

---

## ✅ CERTO

* ControlPlanePaymentCommandService
* ControlPlanePaymentQueryService
* ControlPlanePaymentLifecycleService
* ControlPlanePaymentUpgradeEnqueueService
* PlanUpgradeIdempotencyKeyFactory
* ControlPlanePaymentResponseMapper

---

# 📌 REGRAS IMPORTANTES DO PROJETO

* DDD + Layered (SEM ports & adapters)
* PUBLIC e TENANT NUNCA se cruzam direto
* sempre usar IntegrationService para crossing
* sempre usar AppClock para tempo
* sempre usar ApiException para regra de negócio
* sempre usar idempotência em billing

---

# 🎯 COMO USAR ESTE PADRÃO

Quando tiver uma classe grande:

1. Cole a classe
2. Peça: "quebre essa classe seguindo o padrão do projeto"
3. Cole junto este arquivo

---

# 🔚 RESULTADO ESPERADO

* Código previsível
* Arquitetura consistente
* Sem God Services
* Alta legibilidade
* Fácil manutenção
* Base pronta para escala
