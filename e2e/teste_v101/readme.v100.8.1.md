# V100.8.1 corrigida

Regra preservada:
- **V100.8.1 = V100.8 + correção**
- nenhuma cobertura anterior foi removida

## Correção aplicada
Os requests de query:
- 04.10A query products page 0
- 04.10B query products page 1
- 04.10C query customers list

agora:
- usam headers explícitos no request:
  - `Authorization: Bearer {{tenant_access_token}}`
  - `X-Tenant: {{tenant_schema}}`
- reaplicam os mesmos valores no pre-request
- escrevem `tenant_access_token` e `tenant_schema` também em `collectionVariables` logo após o login tenant

## Arquivos
- `collection.v100.8.1.json`
- `environment.v100.8.1.json`
- `run-teste-v100.8.1-enterprise-data-population-grid-query-stress-patched-strict.sh`
- `run-teste-v100.8.1-enterprise-data-population-grid-query-stress-patched-ultra.sh`

## Como rodar
```bash
cd ~/eclipse-workspace/multitenancy001/e2e/teste_v100.8
chmod +x *.sh
./run-teste-v100.8.1-enterprise-data-population-grid-query-stress-patched-strict.sh
```
