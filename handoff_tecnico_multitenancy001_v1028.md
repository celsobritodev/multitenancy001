# Handoff Técnico Detalhado — Projeto `multitenancy001`
## Continuidade de desenvolvimento backend + evolução E2E enterprise

**Data de referência:** 2026-03-28  
**Projeto:** `multitenancy001`  
**Contexto atual:** backend ainda em evolução, com muitas regras de negócio ainda por implementar; suíte E2E já atingiu nível avançado/enterprise e será retomada conforme as novas regras forem entrando no projeto.

---

# 1. Objetivo deste handoff

Este documento serve para transferir, com o máximo de contexto possível, o estado atual do projeto, o padrão de trabalho adotado, o histórico recente das suítes E2E da linha V100 → V102.8, os problemas reais encontrados, as correções aplicadas, o ponto exato onde paramos e o direcionamento para a próxima fase.

A ideia é que, ao abrir outro chat, este handoff sozinho já permita continuar o trabalho sem perder:

- arquitetura adotada
- padrão de código
- padrão de testes
- convenções de pastas
- forma de execução
- filosofia VN = VN-1 + novas requisições
- critérios de entrega
- padrão de artefatos
- histórico técnico do que já foi validado

---

# 2. Situação atual do projeto

## Estado macro

Hoje o projeto está dividido em duas frentes:

### A. Backend / domínio
O backend **ainda não está finalizado**.  
Ainda faltam várias regras de negócio reais a serem implementadas/refinadas.

### B. Suíte E2E
A suíte E2E evoluiu fortemente e já atingiu um nível alto de maturidade.  
Ela já valida:

- bootstrap do sistema
- signup e criação de tenants
- autenticação tenant
- autenticação control plane
- RBAC
- billing
- population grid por tenant
- categories / subcategories / suppliers / customers / products
- inventory
- sales
- queries
- hardening
- chaos
- smoke final

## Decisão prática tomada
Como **ainda faltam muitas regras de negócio no backend**, a orientação agora é:

> **continuar implementando as regras de negócio no projeto**  
> e **retomar/evoluir as suítes E2E conforme o domínio amadurecer**.

Ou seja:

- primeiro: seguir construindo o sistema
- depois: expandir a suíte novamente

---

# 3. Princípio fundamental do projeto

## Regra principal da evolução de suíte

> **VN = VN-1 + novas requisições**

Essa é a regra mais importante de todas.

### Isso significa:
Uma versão nova da suíte:

- **não pode remover** o que a anterior já testava
- **não pode simplificar** cobertura
- **não pode recomeçar do zero**
- **não pode perder blocos anteriores**
- **não pode regredir cobertura**

### Sempre deve:
- manter tudo que já funcionava
- reaproveitar a base anterior
- apenas adicionar novas capacidades
- aumentar a malha de validação
- endurecer a suíte sem amputar fluxo

---

# 4. Stack e arquitetura do sistema

## Stack técnica
- Java 21
- Spring Boot 3.x
- PostgreSQL
- Multi-tenant schema-per-tenant
- Flyway como fonte da verdade para evolução de banco
- JWT
- RBAC

## Modelo de tenancy
O projeto usa **schema-per-tenant**.

### Cabeçalho obrigatório
```http
X-Tenant: t_tenant_xxx
```

## Separação estrutural
- **Public schema / control plane**
- **Tenant schema / APIs tenant**

## Arquitetura obrigatória
Padrão usado no projeto:

> **DDD / Layered**

### Camadas
- API
- APP
- DOMAIN
- PERSISTENCE
- INFRASTRUCTURE

### Regra obrigatória
> **NÃO usar Ports & Adapters**

---

# 5. Padrão de desenvolvimento exigido

## Como o usuário quer receber código
Sempre seguir estas regras:

### Sempre entregar
- código completo
- pronto para copiar e colar
- dentro do padrão arquitetural do projeto
- com logs
- com Javadoc
- sem depender de remendos manuais

### Nunca entregar
- snippets isolados
- “ajustes pontuais” sem contexto
- remendos cirúrgicos quando o problema pede refatoração
- pedaços incompletos

## Regra de refatoração
Se for necessário corrigir o sistema de verdade:

> pode refatorar o que for necessário

Não existe apego a “não tocar” em outras partes, desde que a correção fique:

- coerente
- estável
- limpa
- alinhada ao padrão do projeto

---

# 6. Padrão de classes no backend

## Logs obrigatórios
As classes devem usar logs consistentes, por exemplo:

- `log.info(...)`
- `log.warn(...)`
- `log.error(...)`

## Javadoc obrigatório
As classes devem ter documentação Javadoc coerente.

## Objetivo
Facilitar:
- manutenção
- diagnóstico
- handoff entre chats
- leitura futura
- auditoria do que cada classe faz

---

# 7. Filosofia dos testes E2E

Esses testes **não são testes unitários**.

Eles são:

> **validação real do sistema rodando**

## Ferramentas usadas
- Postman Collection
- Newman CLI
- Bash scripts
- Git Bash no Windows

## Objetivo da suíte
Validar comportamento real de produção simulada, incluindo:

- integridade entre tenants
- comportamento transacional
- inventory/ledger
- regras de domínio
- billing
- chaos
- resiliência

---

# 8. Ambiente de execução

## Shell usado
Tudo é executado via:

> **Git Bash**

## Padrão de execução
Dentro da pasta da suíte:

```bash
chmod +x *.sh
./run-teste-vXX-strict.sh
./run-teste-vXX-ultra.sh
```

---

# 9. Estrutura de pastas e arquivos E2E

## Estrutura padrão
```text
multitenancy001/
└── e2e/
    └── teste_vXX/
        ├── cleanup.sh
        ├── logs/
        ├── readme.vXX.md
        ├── collection.vXX.json
        ├── environment.vXX.json
        ├── run-teste-vXX-strict.sh
        ├── run-teste-vXX-ultra.sh
        ├── chaos-*.sh
        ├── chaos-*.py
```

## Observações
- `logs/` guarda logs da aplicação e relatórios
- `cleanup.sh` limpa ambiente temporário
- `collection` contém a malha de requests
- `environment` contém variáveis de execução
- `strict` roda carga controlada
- `ultra` roda carga ampliada/chaos
- os helpers `chaos-*` podem existir conforme a suíte evolui

---

# 10. Padrão de entrega exigido

## Regra absoluta
Quando for entregar suíte E2E:

> **sempre entregar `.zip` pronto para download**

## Isso é obrigatório
O usuário quer sempre:
- estrutura pronta
- arquivos organizados
- zero ajuste manual
- suíte já montada
- pronto para baixar e rodar

## Não é desejado
- mandar só collection
- mandar só script
- mandar só parte do pacote
- mandar instruções soltas sem pacote fechado

---

# 11. Resumo das versões recentes

## Linha V100
A linha V100 foi a linha de **population grid pesado**, com preenchimento em massa.

Ela serviu como base para:
- geração forte de tenants
- population de entidades
- consolidação de malha de cadastro
- amadurecimento da suíte

## Linha V101
A linha V101 foi a linha em que ficou clara uma regra de domínio real muito importante:

> **subcategory.categoryId precisa bater com product.categoryId**

Esse foi um bug real detectado pela suíte.

### Problema encontrado na V101.1
- criação de product quebrando com `400`
- causa: subcategory incompatível com category
- backend correto ao bloquear
- regra de domínio confirmada

### Correção consolidada
Na V101.2/V101.3 a suíte passou a:
- mapear categories corretamente
- mapear subcategories corretamente
- montar payloads de product compatíveis com domínio
- usar `price`
- não usar `salePrice` incorretamente

### Resultado
A V101.3 foi homologada como base sólida.

---

# 12. Linha V102 — chaos extremo

A linha V102 surgiu para adicionar caos **em cima da base já estável**.

## Objetivo da linha V102
Adicionar:
- double submit
- replay
- oversell
- reread inventory
- drift sanity
- smoke final

## Evolução da linha V102
Houve várias iterações de ajuste porque os problemas encontrados não eram mais do backend em si, mas da **infra de validação** e do desenho do chaos.

### Problemas enfrentados na linha V102
1. chaos não estava conectado na cadeia
2. runner travava no reset do banco
3. havia falhas artificiais por assert estreita demais
4. houve over-hardening em algumas versões
5. scripts de subcategory/product quebravam por erro de JavaScript
6. algumas versões estavam mais agressivas que o necessário
7. foi necessário recalibrar expectations do chaos

---

# 13. O que ficou validado de verdade na V102

Ao longo da evolução V102.1 → V102.8, foi consolidado:

## Base funcional
- bootstrap
- auth control plane
- signup
- grid control plane users
- grid billing
- login tenant
- tenant user grid
- category grid
- subcategory grid
- supplier grid
- customer grid
- product grid
- inventory inbound
- sales grid
- read sale
- queries
- hard limits

## Hardening validado
- oversell guard sem estoque
- hard user limit
- hard product limit

## Chaos validado
- init chaos
- login do tenant inicial
- escolha de fixture
- baseline de inventory
- double submit A
- double submit B
- replay read sale
- drain inventory
- oversell impossível
- reread inventory
- drift sanity
- smoke final

---

# 14. Aprendizados críticos da linha V102

## 14.1 Chaos não valida um único código de resposta
O ponto mais importante da V102 foi este:

> em chaos, o que importa não é um status exato  
> o que importa é comportamento seguro

### Exemplo
Se uma operação de chaos retorna:
- `200`
- `400`
- `409`
- `422`

dependendo do cenário, **todas podem ser respostas válidas**, desde que o sistema continue:

- consistente
- não-negativo
- sem drift
- sem corrupção de estado

---

## 14.2 Não usar `throw` agressivo em E2E
Ficou claro que usar `throw new Error(...)` em prerequest/test de forma agressiva:

- quebra o fluxo
- gera cascata artificial
- mascara o que o backend realmente fez

A suíte precisa ser forte, mas também resiliente.

---

## 14.3 Binding dinâmico por tenant é obrigatório
Ficou claro que:
- IDs fixos em subcategory são perigosos
- fixtures devem ser dinâmicas por tenant
- category/subcategory/product precisam ser coerentes
- product precisa respeitar a árvore de domínio

---

# 15. Onde paramos exatamente

## Ponto atual
A linha V102 chegou ao estado:

> **V102.8 FINAL CHAOS CALIBRADO**

## Estado desse ponto
A suíte já está em nível enterprise, com:
- malha ampla
- base funcional validada
- chaos calibrado
- hardening validado
- multi-tenant validado
- binding dinâmico validado
- comportamento sistêmico validado

## Porém:
A decisão de negócio/técnica atual é:

> **não continuar expandindo a suíte agora**

Porque:

- ainda faltam várias regras de negócio no backend
- o sistema ainda vai crescer em comportamento
- faz mais sentido continuar evoluindo o domínio antes de expandir demais a malha E2E

---

# 16. Onde pretendemos ir depois

Quando as novas regras de negócio forem implementadas, a suíte deve continuar evoluindo.

## Possíveis próximas linhas

### V103 — rollback / replay
- cancelamento de venda
- rollback de inventory
- validação de reversão de ledger

### V104 — ledger rebuild
- reconstrução de inventory por movements
- detecção de drift real

### V105 — chaos distribuído
- múltiplos workers
- corrida real de vendas
- stress extremo

---

# 17. Regras de ouro para os próximos chats

## Sempre lembrar
Ao continuar em outro chat, seguir estes mandamentos:

### 1. Sempre respeitar
> **VN = VN-1 + novas requisições**

### 2. Sempre entregar
> **.zip completo e pronto para download**

### 3. Sempre usar
> **Git Bash**

### 4. Sempre manter
> **estrutura de pastas já definida**

### 5. Nunca enviar
- snippets isolados
- código parcial
- remendo solto
- solução sem contexto

### 6. Sempre preferir
- código completo
- pronto para copiar e colar
- com logs
- com Javadoc
- no padrão DDD/layered
- sem ports and adapters

### 7. Sempre lembrar
Se a correção exigir refatoração maior:

> pode refatorar

---

# 18. Como iniciar o próximo chat

Sugestão de prompt de continuidade:

```text
Estou continuando o projeto multitenancy001.

Contexto:
- backend ainda não está finalizado; faltam várias regras de negócio
- a suíte E2E já foi evoluída até a linha V102.8
- por enquanto quero seguir implementando regras de negócio no backend e depois continuar a evolução da suíte

Regras obrigatórias:
- seguir VN = VN-1 + novas requisições
- sempre usar meu padrão DDD/layered
- não usar ports and adapters
- sempre entregar código completo pronto para copiar e colar
- classes com logs e Javadoc
- quando for suíte E2E, sempre entregar .zip completo pronto para download
- uso Git Bash
- manter minha estrutura de pastas e arquivos
```

---

# 19. Resumo executivo

## O que fizemos
- consolidamos a linha V100
- corrigimos a linha V101 com regra real de domínio
- evoluímos a linha V102 até um chaos calibrado de nível enterprise
- validamos multi-tenant, product/category/subcategory, inventory, sales, hardening e smoke final

## Onde paramos
- backend ainda com regras de negócio faltando
- suíte já madura o suficiente para pausar expansão e retomar depois

## Próximo foco
- continuar implementando regras de negócio no sistema
- depois evoluir a suíte novamente

---

# 20. Encerramento

Este handoff representa o estado atual do projeto com o máximo de fidelidade possível ao padrão adotado.

A situação real hoje é:

> o backend ainda vai crescer em domínio  
> a suíte já atingiu um nível muito alto de maturidade  
> e a próxima fase correta é continuar construindo o sistema, para depois seguir a expansão E2E com base sólida.

