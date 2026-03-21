# V100 Heavy Data Population Grid - PATCHED

Ajuste aplicado nesta versão:

- Correção do grid `controlplane/users` para usar os valores reais do enum `ControlPlaneRole`.
- `controlplane_roles_json` atualizado para:
  - `CONTROLPLANE_BILLING_MANAGER`
  - `CONTROLPLANE_SUPPORT`
  - `CONTROLPLANE_OPERATOR`
- Mantido o restante da suíte no mesmo padrão operacional.

## Arquivos

- `multitenancy001.postman_collection.v100.heavy-data-population-grid.json`
- `multitenancy001.local.postman_environment.v100.heavy-data-population-grid.json`
- `run-teste-v100-heavy-data-population-grid-strict.sh`
- `run-teste-v100-heavy-data-population-grid-ultra.sh`
- `cleanup.sh`

## Observação

Este patch corrige especificamente o `400 Bad Request` do grid de usuários do control plane causado por payload fora do contrato do enum.
