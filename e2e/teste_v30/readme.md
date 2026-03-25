# V30 HARD LIMITS ENFORCEMENT

V30 gerada corretamente no padrão incremental:

- V30 = V29 + novo bloco 216
- nenhum bloco anterior foi removido
- STRICT = suíte anterior inteira + bloco 216 com cap curto
- ULTRA = suíte anterior inteira + bloco 216 até hard limit real

## Estrutura
- chaos-ledger-rebuild.py
- chaos-node-launch.sh
- chaos-race-aggregate.py
- chaos-race-worker-sale.sh
- cleanup.sh
- logs/
- multitenancy001.local.postman_environment.v30.hard-limits-enforcement.json
- multitenancy001.postman_collection.v30.hard-limits-enforcement.json
- mvnw
- readme.md
- run-teste-v30-strict.sh
- run-teste-v30-ultra.sh

## STRICT
Padrão:
- users cap = 15
- products cap = 30

Customização:
export V30_STRICT_SHORT_USERS_CAP=20
export V30_STRICT_SHORT_PRODUCTS_CAP=40
./run-teste-v30-strict.sh

## ULTRA
Executa a suíte completa e, no bloco 216, vai até o hard limit real.
./run-teste-v30-ultra.sh
