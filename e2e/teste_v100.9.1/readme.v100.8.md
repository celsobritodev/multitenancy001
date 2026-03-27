# V100.8 QUERY STRESS

Esta versão segue sua regra incremental:

- V100.8 = V100.7 + novas requests
- mantém a suíte inteira anterior
- adiciona validações de query/grid sem remover cobertura

## Novas requests adicionadas
- 04.10A query products page 0
- 04.10B query products page 1
- 04.10C query customers list

## Objetivo
Validar que a massa gerada na V100.7 também sustenta consultas reais de grid:

- paginação de products
- avanço de página
- listagem de customers

## Runners
- STRICT:
  `./run-teste-v100.8-enterprise-data-population-grid-query-stress-strict.sh`
- ULTRA:
  `./run-teste-v100.8-enterprise-data-population-grid-query-stress-ultra.sh`

## Como rodar
```bash
cd ~/eclipse-workspace/multitenancy001/e2e/teste_v100.8
chmod +x *.sh
./run-teste-v100.8-enterprise-data-population-grid-query-stress-strict.sh
```
