# 🧾 Handoff Técnico — Auditoria V2 (`detailsJson`)  
## Problema, solução, escopo completo de refatoração e estratégia de execução

---

## 1. Objetivo deste documento

Este documento registra, de forma detalhada, o estado atual da arquitetura de auditoria do projeto `multitenancy001`, o problema estrutural original relacionado a `detailsJson`, o que já foi corrigido, o que ainda existe como limitação arquitetural, e exatamente o que precisaria ser refatorado em uma futura **V2 da arquitetura de auditoria**.

Este material serve para:

- guardar contexto técnico no projeto;
- transferir trabalho para outro chat ou outra pessoa;
- evitar rediscussão do problema já analisado;
- deixar claro o que é **correção crítica já resolvida** e o que é **evolução arquitetural opcional**.

---

## 2. Contexto do projeto

### 2.1 Stack e arquitetura

- Java + Spring Boot + JPA + Flyway + Security
- DDD layered
- Sem ports & adapters
- Multi-tenant com:
  - control plane em schema público
  - tenant schema por conta
- Banco é dropado com frequência
- Flyway é a fonte de verdade da estrutura

### 2.2 Regra de qualidade adotada

No projeto, a auditoria deve seguir estes princípios:

- payloads de auditoria devem ser **estruturados**
- serialização JSON deve ser **centralizada**
- nunca montar JSON “na mão” com concatenação de string
- persistência deve ser defensiva contra entrada malformada
- contratos internos devem ser claros e previsíveis
- logs devem ser legíveis e não induzir pseudo-JSON

---

## 3. Problema original

O problema original não era “usar JSON em auditoria”.

O problema era **usar `String detailsJson` de forma insegura**, com possibilidade de:

- concatenar JSON manualmente;
- escapar conteúdo manualmente;
- passar texto cru adiante sem validação;
- persistir payload inválido;
- depender apenas da disciplina do desenvolvedor.

### 3.1 Forma problemática

Exemplos da forma errada que precisavam ser eliminados:

```java
"{\"raw\":\"" + valor + "\"}"
```

```java
"{\"challengeId\":\"" + challengeId + "\"}"
```

```java
"{\"mode\":\"challenge_confirm\"}"
```

### 3.2 Riscos dessa abordagem

Essa abordagem gerava estes riscos:

- regressão fácil;
- inconsistência entre serviços;
- duplicação de lógica de serialização;
- dificuldade de manutenção;
- persistência de JSON inválido;
- aumento do acoplamento com detalhes de escape;
- falsa sensação de que o dado está estruturado quando, na prática, é apenas uma string.

---

## 4. Diagnóstico feito no projeto

A análise do código e dos greps levou à seguinte conclusão:

### 4.1 Problema crítico já resolvido

Os pontos mais perigosos do sistema foram corrigidos:

- `SecurityAuditTxWriter`
- `AccountProvisioningAuditService`
- `AuthEventAuditService`

Esses pontos agora fazem blindagem local do `detailsJson` antes da persistência.

### 4.2 Como a blindagem foi implementada

Foi adotado o seguinte padrão:

- `normalizeDetailsJson(...)`
- uso de `JsonDetailsMapper`
- encapsulamento de texto cru em:
  - `{"raw": "..."}`
- persistência apenas de conteúdo já tratado

### 4.3 O que os greps provaram

As verificações mostraram que:

- não restou concatenação de JSON manual relevante;
- `setDetailsJson(...)` só ocorre em pontos blindados;
- `normalizeDetailsJson(...)` está presente nos writers/serviços críticos;
- `JsonDetailsMapper` está consolidado em várias camadas do projeto.

---

## 5. Estado atual: o sistema está seguro?

## Resposta curta
Sim.

## Resposta técnica
Sim, o sistema está atualmente em um estado **seguro e aceitável**, porque:

- não existe mais o problema crítico de JSON manual espalhado;
- a persistência sensível foi protegida;
- o pipeline principal de auditoria está consistente;
- o risco imediato foi reduzido de forma importante.

### 5.1 O que isso significa na prática

Hoje, mesmo que alguma chamada interna ainda trabalhe com `String detailsJson`, os pontos finais mais sensíveis de gravação já protegem a persistência contra entrada textual simples ou payload fora do padrão esperado.

---

## 6. O que ainda existe como limitação arquitetural

Apesar do hardening já aplicado, ainda existe uma limitação:

```java
String detailsJson
```

em algumas assinaturas internas de escrita.

### 6.1 Por que isso não é ideal

Porque `String` é um contrato fraco para representar dados estruturados.

### 6.2 Problemas de manter `String detailsJson`

Mesmo com a blindagem atual, manter `String detailsJson` ainda significa:

- tipagem fraca;
- menor clareza semântica;
- possibilidade de má utilização futura;
- necessidade de validação defensiva em mais de um lugar;
- menor orientação de uso correto para novos desenvolvedores.

### 6.3 O que isso NÃO significa

Isso **não** significa que o sistema está quebrado hoje.  
Isso **não** significa que existe bug urgente aberto.  
Isso **não** significa que a refatoração precisa ser feita imediatamente.

Isso significa apenas que ainda existe espaço para evoluir a arquitetura para um modelo mais seguro e explícito.

---

## 7. O que seria a Auditoria V2

A Auditoria V2 é a refatoração que remove `String detailsJson` das **assinaturas internas de escrita** e passa a trabalhar com payload estruturado.

### 7.1 Novo contrato desejado

Substituir:

```java
record(..., String detailsJson)
```

por:

```java
record(..., Map<String, Object> details)
```

ou, futuramente:

```java
record(..., AuditDetails details)
```

### 7.2 Regra central da V2

Na V2:

- serviços e integrações recebem **payload estruturado**;
- somente o último ponto de escrita converte isso para JSON;
- a entidade JPA continua persistindo `detailsJson` como texto JSON;
- o banco não precisa mudar.

---

## 8. O que a V2 resolve

A V2 resolve estes pontos de forma definitiva:

- remove o contrato stringly-typed;
- reduz risco de regressão futura;
- melhora semântica das APIs internas;
- centraliza ainda mais a serialização;
- torna o fluxo mais previsível;
- diminui necessidade de “blindagem repetida” em vários pontos.

---

## 9. O que a V2 NÃO precisa mudar

A V2 não exige alteração em:

- estrutura do banco;
- migrations Flyway;
- colunas `details_json`;
- entidades JPA do ponto de vista de persistência final;
- DTOs de leitura que hoje retornam `detailsJson`.

Ou seja: **é uma refatoração de contratos internos**, não uma mudança de modelo de armazenamento.

---

## 10. Escopo completo da refatoração V2

A refatoração V2 afetaria principalmente 3 trilhas:

1. Security Audit
2. Auth Event Audit
3. Provisioning Audit

---

## 11. Trilha 1 — Security Audit

### 11.1 Situação atual
Hoje a trilha de security ainda usa `detailsJson` em contratos internos, embora o writer final já esteja blindado.

### 11.2 Arquivos principais a revisar
- `SecurityAuditRequestedEvent`
- `SecurityAuditService`
- `ControlPlaneSecurityAuditIntegrationService`
- `SecurityAuditTxWriter`

### 11.3 Mudança desejada
Trocar contratos baseados em:

```java
String detailsJson
```

para:

```java
Map<String, Object> details
```

### 11.4 Resultado esperado
- integrações passam payload estruturado;
- event object passa payload estruturado;
- writer serializa no final;
- `normalizeDetailsJson` pode deixar de ser o principal mecanismo de defesa e passar a ser fallback residual.

---

## 12. Trilha 2 — Auth Event Audit

### 12.1 Situação atual
O fluxo de auth já usa `JsonDetailsMapper` nos chamadores, mas a assinatura do audit service ainda aceita string.

### 12.2 Arquivos principais a revisar
- `AuthEventAuditService`
- `ControlPlaneAuthEventAuditIntegrationService`
- `TenantAuthAuditRecorderPublicSchemaJpa`
- `TenantAuthAuditRecorder`

### 12.3 Mudança desejada
Substituir:

```java
record(..., String detailsJson)
```

por:

```java
record(..., Map<String, Object> details)
```

### 12.4 Resultado esperado
- integração de auth deixa de serializar cedo demais;
- contratos internos passam a refletir corretamente a natureza estruturada do payload;
- serviço de auth audit passa a serializar apenas no ponto final.

---

## 13. Trilha 3 — Provisioning Audit

### 13.1 Situação atual
O onboarding já produz dados estruturados, mas ainda há serialização antecipada em partes do fluxo, seguida de re-normalização.

### 13.2 Arquivos principais a revisar
- `AccountProvisioningAuditService`
- `AccountOnboardingAuditService`

### 13.3 Mudança desejada
Trocar métodos como:

- `started(Long accountId, String message, String detailsJson)`
- `success(Long accountId, String message, String detailsJson)`
- `failed(Long accountId, ..., String detailsJson)`

por métodos com `Map<String, Object> details`.

### 13.4 Resultado esperado
- onboarding passa o `Map` diretamente;
- `AccountProvisioningAuditService` serializa no final;
- desaparece a necessidade de converter para string antes da hora.

---

## 14. O que ficaria igual

Mesmo após a V2, algumas coisas continuariam existindo:

### 14.1 Entidades persistidas continuam com campo JSON em texto
Exemplo conceitual:

- `AccountProvisioningEvent.detailsJson`
- `AuthEvent.detailsJson`
- `PublicSecurityAuditEvent.detailsJson`

### 14.2 DTOs de consulta podem continuar retornando `detailsJson`
Porque isso é uma representação já persistida/serializada.

### 14.3 `JsonDetailsMapper` continua sendo o padrão oficial
A V2 não substitui o mapper.  
Ela **consolida o mapper como única forma correta de serialização**.

---

## 15. Ordem recomendada de execução

Se um dia essa refatoração for executada, a ordem ideal é:

1. Security Audit
2. Auth Event Audit
3. Provisioning Audit

### 15.1 Por que essa ordem
Porque ela reduz quebra em cascata e ataca primeiro os contratos mais centrais e compartilhados.

---

## 16. Estratégia segura de implementação

A execução recomendada é em duas fases.

### 16.1 Fase A — Compatibilidade
- criar overload ou nova assinatura usando `Map<String, Object>`;
- manter temporariamente a assinatura antiga com `String detailsJson`;
- internamente, fazer delegação controlada;
- migrar chamadores aos poucos.

### 16.2 Fase B — Limpeza
- migrar todos os chamadores para a nova assinatura;
- remover o overload antigo;
- rodar grep final para garantir que `String detailsJson` não ficou em contratos internos de escrita.

---

## 17. Vantagens da estratégia em duas fases

- menor risco de quebra;
- compilação mais previsível;
- permite migração incremental;
- facilita revisão;
- reduz retrabalho em cadeia.

---

## 18. Quando essa refatoração vale a pena

A V2 passa a ser boa prioridade quando:

- o projeto vai ter mais desenvolvedores mexendo nessa área;
- haverá nova rodada grande de mudanças em auth/security/audit;
- você quiser aumentar rigidez arquitetural antes de evoluções maiores;
- quiser remover contratos fracos e reduzir manutenção futura.

---

## 19. Quando ela NÃO deve ser prioridade

A V2 **não precisa ser priorizada agora** se o foco atual for:

- regras de negócio;
- estabilização funcional;
- suites E2E;
- cobertura de fluxos críticos;
- entrega de funcionalidades.

---

## 20. Classificação da V2

### 20.1 O que ela é
- melhoria arquitetural;
- endurecimento de contrato;
- evolução de design;
- refatoração de qualidade.

### 20.2 O que ela não é
- bugfix urgente;
- correção crítica bloqueante;
- prioridade obrigatória antes de seguir o projeto.

---

## 21. Resumo executivo

### Problema original
Uso inseguro de `detailsJson`, com risco de JSON manual, escape manual e persistência inconsistente.

### Solução já aplicada
- eliminação de JSON manual;
- adoção de `JsonDetailsMapper`;
- normalização defensiva nos pontos críticos;
- validação via grep.

### Limitação atual
Ainda existem assinaturas internas com `String detailsJson`.

### Solução V2
Trocar essas assinaturas por `Map<String, Object>` ou `AuditDetails`, deixando a serialização apenas no writer final.

### Urgência
Baixa.  
Desejável, mas não crucial.

---

## 22. Decisão recomendada

### Decisão prática para o momento atual
**Não tratar a V2 como obrigatória agora.**

### Recomendação
Manter essa refatoração registrada como backlog técnico arquitetural e executá-la apenas quando houver contexto favorável, especialmente junto com nova rodada de alterações em auditoria/auth/security.

---

## 23. Checklist futuro de execução

Quando decidir fazer a V2, revisar nesta ordem:

### Security Audit
- [ ] `SecurityAuditRequestedEvent`
- [ ] `SecurityAuditService`
- [ ] `ControlPlaneSecurityAuditIntegrationService`
- [ ] `SecurityAuditTxWriter`

### Auth Event
- [ ] `AuthEventAuditService`
- [ ] `ControlPlaneAuthEventAuditIntegrationService`
- [ ] `TenantAuthAuditRecorderPublicSchemaJpa`
- [ ] `TenantAuthAuditRecorder`

### Provisioning
- [ ] `AccountProvisioningAuditService`
- [ ] `AccountOnboardingAuditService`

### Pós-refatoração
- [ ] remover assinaturas antigas com `String detailsJson`
- [ ] validar com grep
- [ ] garantir que só entidades/DTOs de leitura mantêm `detailsJson`

---

## 24. Greps para usar no futuro

### Encontrar contratos ainda baseados em string
```bash
grep -R --line-number --color \
-E "String detailsJson|detailsJson\)" \
src/main/java
```

### Encontrar pontos de normalização
```bash
grep -R --line-number --color \
"normalizeDetailsJson(" src/main/java
```

### Encontrar uso do mapper
```bash
grep -R --line-number --color \
"JsonDetailsMapper" src/main/java
```

### Encontrar persistência final
```bash
grep -R --line-number --color \
"setDetailsJson(" src/main/java
```

---

## 25. Conclusão final

O problema importante de auditoria já foi resolvido.

Hoje o projeto está:

- estável nesse eixo;
- seguro contra a regressão principal;
- coerente com o padrão de serialização centralizada;
- apto a seguir com regras de negócio e testes sem depender da V2.

A Auditoria V2 continua sendo uma boa evolução futura, mas deve ser tratada como:

> **melhoria arquitetural desejável, e não correção crítica obrigatória.**

---

## 26. Frase curta para guardar

**Estado atual: seguro e validado.  
V2: evolução arquitetural opcional, não urgente.**
