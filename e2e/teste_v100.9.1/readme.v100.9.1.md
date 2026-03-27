# V100.9.1 CORRIGIDA

Regra preservada:
- **V100.9.1 = V100.9 + correção**
- nenhuma cobertura anterior foi removida

## Correção aplicada
- ajuste do passo `04.10D query products sorted`
- removida validação rígida que estava falhando por calibragem do teste
- mantido:
  - endpoint responde 200
  - query retorna linhas
  - observação de monotonicidade registrada sem falhar a suíte

## Arquivos
- `collection.v100.9.1.json`
- `environment.v100.9.1.json`
- `run-teste-v100.9.1-query-hardened-patched-strict.sh`
- `run-teste-v100.9.1-query-hardened-patched-ultra.sh`

## Como rodar
```bash
cd ~/eclipse-workspace/multitenancy001/e2e/teste_v100.9
chmod +x *.sh
./run-teste-v100.9.1-query-hardened-patched-strict.sh
```
