# V16.1.6 - ENTERPRISE + INVENTORY RACE CONDITIONS STRICT SUITE

Hotfix da V16.1.4.

## Correções aplicadas

- remove referências residuais a `json` fora do escopo
- remove `}` órfãos que quebravam os test scripts de sales
- recompõe os testes de capture de sale ids
- preserva a mesma estrutura da suíte

## Execução

```bash
./run-teste-v16.1.6-enterprise-inventory-race-conditions.sh
```

```bash
./run-teste-v16.1.6-enterprise-inventory-race-conditions-ultra.sh
```
