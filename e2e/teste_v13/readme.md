# teste_v13

Suite v13.0 baseada na v12.6, agora com INVENTORY.

## O que esta versão adiciona
- consulta de inventory por product
- ajuste manual INBOUND
- ajuste manual OUTBOUND
- histórico de movimentações
- validação de INSUFFICIENT_STOCK
- impacto de SALE no estoque
- isolamento tenant para inventory

## Arquivos
- multitenancy001.postman_collection.v13.0.enterprise-inventory.json
- multitenancy001.local.postman_environment.v13.0.enterprise-inventory.json
- run-teste-v13-enterprise-inventory.sh
- run-teste-v13-enterprise-inventory-ultra.sh
- cleanup.sh

## Execução
```bash
cd e2e/teste_v13
./run-teste-v13-enterprise-inventory.sh
```

## Observação
Esta suite mantém todos os testes da v12.6 e adiciona apenas o primeiro bloco incremental de inventory.
