# multitenancy001

## Plataforma Determinística de Validação E2E

------------------------------------------------------------------------

# 1. Visão Geral

Este documento descreve a **arquitetura, operação e evolução da suíte
E2E determinística** do projeto **multitenancy001**, um SaaS
**multi‑tenant**.

A suíte E2E não é apenas um conjunto de testes API.\
Ela funciona como uma **plataforma de validação determinística** para
garantir:

-   funcionamento correto dos endpoints
-   isolamento entre tenants
-   consistência de inventory
-   consistência de ledger
-   segurança contra IDOR
-   concorrência real
-   rollback seguro
-   reconstrução de ledger
-   detecção de drift
-   certificação de release

------------------------------------------------------------------------

# 2. Arquitetura do Sistema

## Control Plane

Responsável por:

-   accounts
-   login identities
-   billing
-   provisioning

## Tenant Schemas

Cada tenant possui um schema separado contendo:

-   users
-   customers
-   categories
-   suppliers
-   products
-   inventory
-   movements
-   sales
-   sale_items

------------------------------------------------------------------------

# 3. Filosofia da Plataforma

Testes tradicionais validam apenas:

    request → response

Porém sistemas SaaS precisam validar:

    request → efeitos → concorrência → persistência → invariantes

Exemplo de problema detectado pela suíte:

### Corrida de estoque

Dois workers vendem o mesmo SKU simultaneamente.

Possíveis falhas:

-   oversell
-   estoque negativo
-   dupla baixa

### Drift de ledger

    inventory != sum(movements)

------------------------------------------------------------------------

# 4. Regra de Evolução da Suíte

Toda evolução segue a regra:

    V(N+1) = V(N) + novos testes + novos artefatos

Isso garante:

-   evolução incremental
-   ausência de regressões
-   histórico reproduzível

### Operações proibidas

❌ recriar suíte do zero\
❌ remover requests existentes\
❌ reduzir cobertura\
❌ reorganizar estrutura arbitrariamente

------------------------------------------------------------------------

# 5. Estrutura de Diretórios

    multitenancy001/
     └─ e2e/
         ├─ docs/
         ├─ teste_v26/
         ├─ teste_v27/
         └─ teste_v28/

## Estrutura de uma suíte

    teste_v28/

    cleanup.sh
    mvnw
    readme.md

    run-teste-v28-strict.sh
    run-teste-v28-ultra.sh

    chaos-ledger-rebuild.py
    chaos-node-launch.sh
    chaos-race-aggregate.py
    chaos-race-worker-sale.sh

    logs/

    multitenancy001.postman_collection.v28.cancel-return-reversal-grid.json
    multitenancy001.local.postman_environment.v28.cancel-return-reversal-grid.json

------------------------------------------------------------------------

# 6. Convenções de Nome

## Collections

    multitenancy001.postman_collection.vNN.<slug>.json

## Environments

    multitenancy001.local.postman_environment.vNN.<slug>.json

## Runners

    run-teste-vNN-strict.sh
    run-teste-vNN-ultra.sh

## Chaos Scripts

    chaos-ledger-rebuild.py
    chaos-node-launch.sh
    chaos-race-aggregate.py
    chaos-race-worker-sale.sh

------------------------------------------------------------------------

# 7. Pipeline de Execução

Todos os runners executam o seguinte pipeline.

## 1. Verificação de requisitos

Ferramentas necessárias:

-   Node.js
-   Newman
-   Python
-   Java
-   PostgreSQL tools

## 2. Verificação da porta

    porta 8080

Se ocupada → abortar.

## 3. Reset do banco

Banco utilizado:

    db_multitenancy

Comandos:

    DROP DATABASE
    CREATE DATABASE

## 4. Preparação do Environment

Gerar:

    .env.effective.json

Contendo:

-   tokens
-   tenant ids
-   credenciais

## 5. Start da aplicação

    ./mvnw spring-boot:run

## 6. Health check

    /actuator/health

Esperado:

    status = UP

## 7. Execução Newman

    newman run collection.json -e environment.json

## 8. Chaos Testing

Workers concorrentes executam:

    POST /sales

## 9. Agregação de logs

Script responsável:

    chaos-race-aggregate.py

## 10. Validação de consistência

Comparar:

    inventory
    sum(movements)

------------------------------------------------------------------------

# 8. Como Rodar os Testes

Executar via **Git Bash**.

    cd ~/eclipse-workspace/multitenancy001/e2e/teste_v28

## Modo Strict

    ./run-teste-v28-strict.sh

Executa:

-   boot da aplicação
-   execução Newman
-   validação determinística

## Modo Ultra

    ./run-teste-v28-ultra.sh

Executa:

-   strict suite
-   chaos race
-   rebuild de ledger
-   validação pós‑concorrência

------------------------------------------------------------------------

# 9. Arquitetura Chaos

Workers simulam concorrência real.

Exemplo:

Worker 1

    POST /sales

Worker 2

    POST /sales

Worker 3

    POST /sales

Podendo chegar a:

    100 workers

Cada worker possui:

-   workerId
-   correlationId
-   timestamp
-   sku

------------------------------------------------------------------------

# 10. Invariantes do Sistema

### Invariante 1

    inventory >= 0

### Invariante 2

    inventory == sum(movements)

### Invariante 3

Integridade da cadeia de movements.

### Invariante 4

Rollback seguro.

------------------------------------------------------------------------

# 11. Roadmap Técnico

## V28

Cancel / Return / Reversal Grid

## V29

Multi‑product chaos

## V30

Distributed node chaos

## V31

Transaction abort testing

## V32

Ledger rebuild platform

## V33

Drift detector

## V34

Failure forensics

## V35

Chaos certification

------------------------------------------------------------------------

# 12. Regras de Engenharia

Toda nova versão deve:

-   preservar estrutura
-   preservar requests
-   preservar naming

Toda entrega deve conter:

    ZIP completo da suíte

Nunca entregar:

-   snippets
-   arquivos incompletos
-   estrutura parcial

------------------------------------------------------------------------

# Fim da Documentação
