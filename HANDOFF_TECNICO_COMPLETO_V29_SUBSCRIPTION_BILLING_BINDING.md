# HANDOFF TÉCNICO COMPLETO — V29 SUBSCRIPTION / BILLING BINDING SUITE

## 1. Objetivo deste documento

Este documento consolida, em formato de handoff técnico oficial, o estado atual da suíte **V29** do projeto **multitenancy001**, incluindo:

- contexto arquitetural do projeto
- padrão obrigatório de desenvolvimento e de E2E
- estado atual do backend
- estado atual da suíte V29
- diagnóstico técnico da execução já realizada
- contratos reais já confirmados
- plano objetivo de correção
- entregáveis esperados no próximo ciclo
- prompt final pronto para continuidade em outro chat

Este material deve servir como **documento-base de transição**, preservando o padrão do projeto e evitando perda de contexto.

---

## 2. Contexto geral do projeto

O projeto **multitenancy001** é um SaaS **multi-tenant**, com as seguintes premissas estruturais e operacionais:

### 2.1 Arquitetura
- **Control Plane** no schema `public`
- **Tenant schema** isolado por conta
- arquitetura **DDD/layered**
- **sem ports & adapters**
- separação clara entre camadas
- contratos explícitos entre API, aplicação, domínio e persistência
- validações contratuais e comportamento orientado a regras de negócio reais

### 2.2 Source of truth
- **Flyway** é o source of truth do banco
- a evolução do banco deve respeitar migrations reais
- deve-se evitar remendos temporários se a correção correta for na base estrutural

### 2.3 Prática operacional obrigatória
A rotina operacional do projeto considera que:

- o banco é frequentemente **dropado**
- tudo é recriado **do zero**
- o ambiente precisa funcionar de forma **determinística**
- os testes E2E devem ser robustos o suficiente para suportar:
  - reset completo
  - bootstrap completo
  - recriação integral do fluxo
  - validação do sistema como ele realmente é executado

---

## 3. Padrão obrigatório de desenvolvimento

No próximo ciclo de trabalho, e em qualquer continuação deste esforço, o padrão abaixo é obrigatório.

### 3.1 Backend
- manter o projeto em **DDD/layered**
- **não** introduzir ports & adapters
- entregar **classes completas**
- incluir **Javadoc**
- incluir logs úteis
- fornecer código pronto para copiar e colar
- evitar “ajustes cirúrgicos” isolados quando o correto for entregar o arquivo completo
- **não remover funcionalidades existentes**
- considerar sempre o cenário de **drop do banco**
- preferir corrigir **migrations-base** quando isso for estruturalmente mais correto

### 3.2 E2E / Newman / Postman
- sempre gerar **`.zip` pronto para download**
- cada versão deve ficar dentro de **uma única pasta**
- seguir rigorosamente o padrão material das suítes anteriores
- usar a suíte anterior como base estrutural real
- não gerar starter vazio
- não gerar placeholder pobre
- não quebrar bootstrap/auth herdado que já funciona

---

## 4. Padrão material obrigatório das suítes E2E

Cada suíte deve seguir a estrutura abaixo:

```bash
e2e/teste_vXX/
```

Conteúdo esperado da pasta:

```bash
cleanup.sh
logs/
multitenancy001.local.postman_environment.vXX....json
multitenancy001.postman_collection.vXX....json
mvnw
readme.md
run-teste-vXX-strict.sh
run-teste-vXX-ultra.sh
```

### 4.1 Regras desse padrão
- tudo deve ficar dentro de **uma única pasta**
- os nomes dos arquivos devem seguir o padrão já consolidado
- os scripts devem ser executáveis no **Git Bash**
- os runners precisam subir a aplicação corretamente a partir da raiz real do projeto
- a collection deve ser aderente ao contrato real do backend
- o environment deve ser coerente com a collection e com o bootstrap

---

## 5. Posicionamento da V29 no roadmap

### 5.1 Sequência funcional principal
- **V28** = cancel / return / reversal grid
- **V29** = subscription / billing binding suite

### 5.2 Linha paralela
- **V100+** = data population / massa pesada / volume / stress
- essa linha é paralela e **não substitui** a trilha funcional principal

### 5.3 Conclusão
A próxima suíte funcional correta, dentro da linha principal, é a **V29**, e não a V100.

---

## 6. Objetivo funcional da V29

A V29 existe para validar a feature nova de:

- subscription
- plan change
- preview de mudança de plano
- upgrade via billing binding
- downgrade elegível
- downgrade bloqueado
- validação de entitlements
- validação do binding entre payment e plano

Em outras palavras, a V29 deve comprovar, por E2E, que o backend atual de assinatura e cobrança vinculada ao plano está funcionando conforme o contrato real.

---

## 7. Estado do backend antes da correção da collection V29

O backend já está em estágio bom para esse escopo funcional.

### 7.1 Pacote principal
```text
brito.com.multitenancy001.controlplane.accounts.app.subscription
```

### 7.2 Classes já existentes
- `PlanChangeType`
- `PlanEligibilityViolationType`
- `PlanLimitSnapshot`
- `PlanUsageSnapshot`
- `PlanEligibilityViolation`
- `PlanEligibilityResult`
- `SubscriptionPlanCatalog`
- `PlanChangePolicy`
- `AccountStorageUsageResolver`
- `DefaultAccountStorageUsageResolver`
- `AccountPlanUsageService`
- `ChangeAccountPlanCommand`
- `AccountPlanChangeResult`
- `AccountPlanChangeService`
- `AccountEntitlementsSynchronizationService`

### 7.3 Regras já implementadas no backend
- preview de plan change
- classificação de mudança:
  - `UPGRADE`
  - `DOWNGRADE`
  - `NO_CHANGE`
- bloqueio de downgrade por excesso de uso
- sincronização de `account_entitlements`
- upgrade aprovado via billing
- binding do billing no `Payment`

---

## 8. Endpoints de subscription já criados

### 8.1 Tenant
- `GET /api/tenant/subscription/me/limits`
- `POST /api/tenant/subscription/me/change-plan-preview`
- `POST /api/tenant/subscription/me/change-plan`

### 8.2 Control Plane
- `GET /api/controlplane/accounts/{accountId}/subscription`
- `GET /api/controlplane/accounts/{accountId}/subscription/limits`
- `POST /api/controlplane/accounts/{accountId}/subscription/preview-change`
- `POST /api/controlplane/accounts/{accountId}/subscription/change`

---

## 9. Billing binding já implantado no backend

### 9.1 Enums já existentes
- `BillingCycle`
- `PaymentPurpose`

### 9.2 Payment expandido com os campos
- `targetPlan`
- `billingCycle`
- `paymentPurpose`
- `planPriceSnapshot`
- `effectiveFrom`
- `coverageEndDate`

### 9.3 DTOs de billing atualizados
- `PaymentRequest`
- `AdminPaymentRequest`
- `PaymentResponse`

### 9.4 Serviços ajustados
O `ControlPlanePaymentService` já foi atualizado para:

- validar billing binding
- criar pagamentos com metadados de plano
- ao completar `PLAN_UPGRADE`, chamar:
  - `AccountPlanChangeService.applyApprovedUpgrade(...)`

O `ControlPlanePaymentQueryService` também já foi ajustado ao novo `PaymentResponse`.

---

## 10. Conclusão sobre o backend

O estado objetivo do backend é:

- o projeto **compila**
- a estrutura de **subscription / billing binding** está pronta o suficiente para validação por E2E
- o foco principal agora não é inventar backend novo
- o foco principal é **validar o contrato real via suíte aderente**

---

## 11. Estado estrutural já criado para a V29

A V29 já foi criada estruturalmente com os arquivos:

```bash
cleanup.sh
logs/
multitenancy001.local.postman_environment.v29.subscription-billing-binding.json
multitenancy001.postman_collection.v29.subscription-billing-binding.json
mvnw
readme.md
run-teste-v29-strict.sh
run-teste-v29-ultra.sh
```

---

## 12. Problema inicial observado na V29

As primeiras entregas da V29 estavam inadequadas porque vieram:

- vazias
- pobres
- com collection quase sem conteúdo
- com runners simplificados demais
- fora do padrão real da V28

Esse problema foi corrigido apenas parcialmente.

---

## 13. O que já foi corrigido com sucesso na V29

### 13.1 Correção do runner
O runner da V29 foi corrigido para:

- usar `SCRIPT_DIR`
- usar `PROJECT_ROOT`
- subir a aplicação a partir da raiz correta do projeto
- criar link para `mvnw` da raiz
- usar timeout explícito
- monitorar log da aplicação
- executar health check
- gerar `.env.effective.json`
- gerar report Newman

### 13.2 Resultado da correção
Após essa correção:

- a aplicação subiu corretamente
- o health check passou
- a suíte executou

### 13.3 Conclusão
O problema atual **não é mais o boot da aplicação**.  
O problema atual está concentrado em:

- collection
- bootstrap
- autenticação
- desenho do fluxo E2E

---

## 14. Resultado real da execução da V29

A execução da V29 já produziu resultados concretos:

- aplicação subiu corretamente
- health check passou
- Newman executou a collection
- a suíte rodou inteira
- houve **56 requests**
- houve **43 falhas de assertion**

---

## 15. Padrão dominante dos erros observados

Os erros observados se concentraram em:

- `400` no signup
- `401 Unauthorized` em praticamente todo o fluxo tenant
- URLs quebradas com `accounts//subscription`
- `500` final em support/operator por `accountId` vazio

---

## 16. Diagnóstico técnico preciso

### 16.1 Erro raiz 1 — `00.02 signup` falhou com 400
Esse foi o **primeiro erro real** da suíte.

Se o signup falha:
- não cria conta
- não cria tenant
- não popula variáveis fundamentais
- não produz um estado válido para o restante do bootstrap

### 16.2 Erro raiz 2 — `00.03 tenant login` falhou com 401
Como o signup falhou antes, o login tenant passou a operar em cima de contexto inválido.

Consequências:
- `tenant_access_token` não foi preenchido
- `tenant_refresh_token` não foi preenchido
- `tenant_account_id` não foi preenchido
- `tenant_schema` não foi preenchido

### 16.3 Erro raiz 3 — cascata de falhas tenant
Com autenticação tenant inválida, falharam em sequência:

- `tenant me`
- `subscription/me/limits`
- `change-plan-preview`
- `change-plan`
- CRUDs auxiliares do tenant
- cenários de downgrade

Isso **não prova** que o módulo de subscription está quebrado.  
Isso prova que o **bootstrap tenant da V29 está desalinhado**.

### 16.4 Erro raiz 4 — `tenant_account_id` ficou vazio
Sem signup e login tenant válidos, várias URLs foram construídas assim:

```text
/api/controlplane/accounts//subscription
```

Isso contaminou:
- admin get subscription
- admin limits
- admin preview
- admin change
- billing account queries
- final consistency checks

### 16.5 Erro raiz 5 — support/operator finais mal desenhados
No final da suíte, houve chamadas como:

```text
GET /api/controlplane/accounts/
```

com `accountId` vazio, gerando `500`.

Isso foge do padrão da V28, que usa endpoints mais seguros, como:

- `GET /api/controlplane/me`
- `GET /api/controlplane/accounts`
- `GET /api/controlplane/users`

### 16.6 Erro raiz 6 — refresh/logout fora do contrato real
Alguns fluxos da V29 foram modelados com suposições genéricas.

Na V28, `refresh` e `logout` seguem o contrato real do backend, com:
- body correto
- nomes corretos de variáveis
- uso correto dos tokens

Na V29 isso não foi preservado de forma fiel.

---

## 17. Conclusão técnica do diagnóstico

### 17.1 O que está bom
- o runner da V29 está bom
- a aplicação sobe
- o health check passa
- a suíte executa

### 17.2 O que ainda não foi validado
O backend de **subscription / billing binding** ainda **não foi validado funcionalmente de verdade**, porque a suíte falhou antes de chegar na parte central da feature.

### 17.3 Causa principal atual
A causa principal atual é:

- collection V29 desalinhada com o contrato real do backend
- bootstrap/auth mal reconstruído
- ausência de guards adequados
- reaproveitamento incompleto do padrão real da V28

### 17.4 Conclusão objetiva
O problema atual **não é principalmente do módulo subscription**.  
O problema atual é **de modelagem do E2E da V29**.

---

## 18. Contratos reais confirmados

A partir da V28 e do backend atual, já foram confirmados os seguintes contratos reais.

### 18.1 Signup real esperado pelo backend

```json
{
  "displayName": "Tenant E2E",
  "loginEmail": "{{tenant_email}}",
  "taxIdType": "{{tenant_tax_id_type}}",
  "taxIdNumber": "{{tenant_tax_id_number}}",
  "password": "{{tenant_password}}",
  "confirmPassword": "{{tenant_password}}"
}
```

### 18.2 Observações importantes do signup
- `loginEmail` é o nome correto do campo
- `taxIdType` é enum, como `CPF` ou `CNPJ`
- `password` precisa obedecer a regex real da aplicação
- `confirmPassword` precisa casar com `password`

### 18.3 Tenant login real

```json
{
  "email": "{{tenant_email}}",
  "password": "{{tenant_password}}"
}
```

### 18.4 Tenant refresh real

```json
{
  "refreshToken": "{{tenant_refresh_token}}"
}
```

### 18.5 Tenant logout real

```json
{
  "refreshToken": "{{tenant_refresh_token}}",
  "allDevices": false
}
```

### 18.6 Control Plane logins reais

#### Superadmin
```json
{
  "email": "superadmin@platform.local",
  "password": "admin123"
}
```

#### Billing
```json
{
  "email": "billing@platform.local",
  "password": "admin123"
}
```

#### Support
```json
{
  "email": "support@platform.local",
  "password": "admin123"
}
```

#### Operator
```json
{
  "email": "operator@platform.local",
  "password": "admin123"
}
```

### 18.7 Control Plane refresh real

```json
{
  "refreshToken": "{{superadmin_refresh}}"
}
```

### 18.8 Control Plane logout real

```json
{
  "refreshToken": "{{superadmin_refresh_new}}"
}
```

---

## 19. O que precisa ser feito agora

O trabalho correto agora **não é inventar mais uma suíte genérica**.  
O trabalho correto é **reconstruir a V29 em cima do contrato real da V28 + contrato real do backend atual**.

### 19.1 Prioridade 1 — reconstruir o bloco `00 - BOOTSTRAP`
Usar o contrato real da V28 para:

- signup
- tenant login
- tenant me
- controlplane login
- billing login
- support login
- operator login

### 19.2 Prioridade 2 — corrigir `00.02 signup`
Esse é o primeiro ponto crítico.

A request precisa usar exatamente o payload real do `SignupRequest`.

### 19.3 Prioridade 3 — corrigir o fluxo tenant auth
Reaproveitar da V28 o fluxo correto de:

- tenant login
- tenant refresh
- tenant logout
- tenant me

### 19.4 Prioridade 4 — corrigir controlplane refresh/logout
Seguir exatamente o contrato real usado na V28.

### 19.5 Prioridade 5 — adicionar guards
A collection precisa falhar cedo se faltar qualquer variável crítica, como:

- `tenant_access_token`
- `tenant_refresh_token`
- `tenant_account_id`
- `tenant_schema`
- `superadmin_token`

Regras desejadas:
- se signup falhar, não prosseguir
- se login tenant falhar, não seguir nos blocos tenant
- se `tenant_account_id` estiver vazio, não montar URL de account-specific endpoint
- se `superadmin_token` estiver vazio, não seguir nos blocos admin

### 19.6 Prioridade 6 — remover URLs inválidas
Nenhuma request deve construir caminhos como:

```text
accounts//...
```

### 19.7 Prioridade 7 — corrigir support/operator finais
Esses blocos devem seguir o padrão seguro da V28 e não usar endpoint com `accountId` vazio.

### 19.8 Prioridade 8 — só depois validar subscription
Somente depois do bootstrap/auth estarem corretos devem ser revalidados os cenários de:

- limits
- preview
- upgrade
- admin subscription
- downgrade elegível
- downgrade bloqueado
- billing metadata

---

## 20. Ordem recomendada de continuidade

### Fase 1 — corrigir bootstrap da V29
Objetivo:
- fazer signup funcionar
- fazer tenant login funcionar
- preencher environment corretamente

### Fase 2 — corrigir auth herdado
Objetivo:
- tenant refresh/logout
- controlplane refresh/logout
- support/operator access pattern

### Fase 3 — endurecer guards
Objetivo:
- impedir cascata de erros com environment vazio

### Fase 4 — validar novamente a V29
Objetivo:
- garantir autenticação tenant real
- garantir `tenant_account_id`
- garantir `tenant_schema`

### Fase 5 — validar o miolo subscription / billing binding
Objetivo:
- limits
- preview
- upgrade
- admin subscription
- downgrade block / eligible
- billing metadata

---

## 21. Verdades oficiais para o próximo ciclo

O próximo chat deve considerar como estado oficial:

### 21.1 Backend
- compila
- subscription / billing binding está implementado
- runner da V29 já está resolvido
- o problema atual não é boot da aplicação

### 21.2 E2E
- a V29 já existe como estrutura de pasta única
- a execução já aconteceu
- o primeiro erro real é `signup 400`
- os `401` seguintes são efeito cascata
- houve URLs com `accounts//...`
- os blocos finais de support/operator estão mal desenhados

---

## 22. Entregáveis esperados no próximo chat

A próxima entrega precisa conter:

- `.zip` pronto para download
- uma única pasta `teste_v29/`
- collection corrigida
- environment corrigido
- runners mantidos
- bootstrap herdado corretamente da V28
- guards de variáveis
- remoção de URLs inválidas
- aderência ao contrato real do backend

### 22.1 Restrições
- sem placeholder pobre
- sem starter vazio
- sem fluxo inventado
- sem quebrar padrão já consolidado

---

## 23. Prompt pronto para colar no próximo chat

```text
Continuar a correção da V29 do projeto multitenancy001.

IMPORTANTE:
- seguir exatamente meu padrão de suites E2E
- tudo dentro de uma única pasta `teste_v29/`
- sempre gerar `.zip` pronto para download
- não gerar starter vazio
- não gerar placeholder pobre
- usar a V28 como base estrutural real
- manter runners da V29 que já foram corrigidos
- o problema atual não é mais o boot da app; é a collection/desenho do fluxo

ESTADO ATUAL REAL:
1. O backend compila.
2. O módulo de subscription / plan change / billing binding já está implementado no backend.
3. A V29 já foi executada com sucesso no runner.
4. A aplicação subiu e passou no health check.
5. O problema atual é a collection V29.

CONTRATOS REAIS CONFIRMADOS:
- signup real:
  {
    "displayName": "Tenant E2E",
    "loginEmail": "{{tenant_email}}",
    "taxIdType": "{{tenant_tax_id_type}}",
    "taxIdNumber": "{{tenant_tax_id_number}}",
    "password": "{{tenant_password}}",
    "confirmPassword": "{{tenant_password}}"
  }

- tenant login real:
  {
    "email": "{{tenant_email}}",
    "password": "{{tenant_password}}"
  }

- tenant refresh real:
  {
    "refreshToken": "{{tenant_refresh_token}}"
  }

- tenant logout real:
  {
    "refreshToken": "{{tenant_refresh_token}}",
    "allDevices": false
  }

- controlplane refresh real:
  {
    "refreshToken": "{{superadmin_refresh}}"
  }

- controlplane logout real:
  {
    "refreshToken": "{{superadmin_refresh_new}}"
  }

DIAGNÓSTICO DA EXECUÇÃO:
- `00.02 signup` falhou com 400
- por causa disso, `00.03 tenant login` falhou com 401
- depois disso, quase todo o bloco tenant falhou com 401
- `tenant_account_id` ficou vazio
- várias URLs ficaram `accounts//subscription`
- support/operator finais bateram em endpoint inválido com id vazio e geraram 500

CONCLUSÃO:
- o runner está bom
- o backend ainda não foi validado funcionalmente porque o bootstrap da V29 está errado
- a V29 precisa ser reconstruída em cima do contrato real da V28 + do backend atual

FAZER AGORA:
1. Corrigir `00 - BOOTSTRAP` da V29 usando o contrato real da V28
2. Corrigir `00.02 signup` para usar o payload real do `SignupRequest`
3. Corrigir `00.03 tenant login` e fluxo tenant auth herdando o padrão real da V28
4. Corrigir controlplane refresh/logout seguindo a V28
5. Adicionar guards para não continuar se faltarem:
   - tenant_access_token
   - tenant_refresh_token
   - tenant_account_id
   - tenant_schema
   - superadmin_token
6. Remover URLs inválidas com `accounts//...`
7. Corrigir blocos finais de support/operator para seguir o padrão da V28
8. Só depois disso validar novamente os blocos de subscription:
   - limits
   - preview
   - upgrade
   - admin subscription
   - downgrade block / eligible
   - billing metadata

ENTREGAR:
- `.zip` pronto para download
- com uma única pasta `teste_v29/`
- com collection corrigida
- com environment corrigido
- com runners mantidos
- no mesmo padrão material da V28
```

---

## 24. Resumo executivo final

A frente de backend de **subscription / billing binding** está em bom estado e o projeto compila. A V29 já saiu da fase de “não sobe” e entrou na fase de diagnóstico real. O runner da suíte foi corrigido e está funcionando. A falha atual está concentrada na **collection**, principalmente no **bootstrap/auth**, com primeiro erro em `signup 400`, seguido de cascata de `401` e URLs inválidas por ausência de `tenant_account_id`.

O próximo trabalho correto é:

- reconstruir a V29 em cima do contrato real da V28
- alinhar a collection ao backend atual
- endurecer guards
- revalidar bootstrap
- só então validar subscription / billing binding

Tudo isso deve ser entregue:

- dentro de uma única pasta
- no padrão material do projeto
- com `.zip` pronto para download
- sem placeholders
- sem fluxos genéricos desalinhados do contrato real

---

## 25. Como utilizar este documento

Este arquivo pode ser usado de três formas:

### 25.1 Como documentação interna do projeto
Salvar este `.md` dentro do repositório para registrar o estado técnico da V29.

### 25.2 Como handoff para outro chat
Copiar o conteúdo completo ou pelo menos a seção “Prompt pronto para colar no próximo chat”.

### 25.3 Como checklist operacional
Usar as seções:
- diagnóstico
- prioridades
- ordem de continuidade
- entregáveis esperados

como checklist para reconstrução da suíte.

---

## 26. Nome sugerido do arquivo

Sugestão de nome para guardar no projeto:

```text
HANDOFF_TECNICO_COMPLETO_V29_SUBSCRIPTION_BILLING_BINDING.md
```

Ou, se quiser um nome mais curto:

```text
handoff_v29_subscription_billing_binding.md
```
