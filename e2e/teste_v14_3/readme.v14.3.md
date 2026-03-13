# E2E TEST SUITE V14.3 - STRICT

Projeto: **multitenancy001**

## Objetivo
A V14.3 é a evolução strict da suíte enterprise + inventory hardening.

## O que esta versão faz
- herda a base aprovada da V13
- adiciona o bloco **20 - INVENTORY HARDENING**
- remove o caráter **diagnostic**
- ajusta nomes, banner e README
- endurece novamente os asserts do bloco 20
- executa `clear` antes do início
- mostra o path do script
- corrige o relatório ultra para mostrar a versão certa e tempos reais

## Arquivos
- `multitenancy001.postman_collection.v14.3.enterprise-inventory-hardening.strict.json`
- `multitenancy001.local.postman_environment.v14.3.enterprise-inventory-hardening.strict.json`
- `run-teste-v14.3-enterprise-inventory-hardening.sh`
- `run-teste-v14.3-enterprise-inventory-hardening-ultra.sh`
- `cleanup.sh`

## Execução
### Runner simples
```bash
./run-teste-v14.3-enterprise-inventory-hardening.sh
```

### Runner ultra
```bash
./run-teste-v14.3-enterprise-inventory-hardening-ultra.sh
```
