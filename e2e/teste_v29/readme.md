# V29 - SUBSCRIPTION / BILLING BINDING SUITE

Suite E2E construída no mesmo padrão operacional da V28 e inteira dentro de `teste_v29/`.

## Conteúdo
- cleanup.sh
- logs/
- multitenancy001.local.postman_environment.v29.subscription-billing-binding.json
- multitenancy001.postman_collection.v29.subscription-billing-binding.json
- mvnw (link criado pelos runners)
- readme.md
- run-teste-v29-strict.sh
- run-teste-v29-ultra.sh

## Blocos da collection
- 00 - BOOTSTRAP (V3)
- 01 - TENANT AUTH TESTS (V3)
- 02 - CONTROL PLANE AUTH TESTS
- 03 - TENANT SUBSCRIPTION LIMITS
- 04 - TENANT PLAN CHANGE PREVIEW
- 05 - TENANT UPGRADE VIA BILLING
- 06 - CONTROL PLANE SUBSCRIPTION ADMIN
- 07 - SEED USAGE FOR DOWNGRADE BLOCK
- 08 - DOWNGRADE BLOCK / ELIGIBLE
- 09 - BILLING PAYMENT METADATA VALIDATION
- 10 - FINAL CONSISTENCY CHECKS

## Observação
A suíte já vem preenchida com requests, variáveis de environment e runners robustos.
Alguns endpoints auxiliares de CRUD e queries podem precisar ajuste fino conforme o estado exato do backend e das permissões do projeto.
