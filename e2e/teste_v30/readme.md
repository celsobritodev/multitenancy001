# TESTE V30 - HARD LIMITS ENFORCEMENT

## Objetivo
A V30 evolui a V29 e adiciona validação real de hard limits de subscription:

- bloqueio real de criação de usuário acima do limite
- bloqueio real de criação de produto acima do limite
- coerência de `remainingUsers` e `remainingProducts`
- downgrade bloqueado por uso real
- validação pós-erro sem 5xx e sem corrupção de estado

## Estrutura
- `multitenancy001.postman_collection.v30.hard-limits-enforcement.json`
- `multitenancy001.local.postman_environment.v30.hard-limits-enforcement.json`
- `run-teste-v30-strict.sh`
- `run-teste-v30-ultra.sh`
- `cleanup.sh`
- `chaos-race-worker-sale.sh`
- `chaos-race-aggregate.py`
- `chaos-node-launch.sh`
- `chaos-ledger-rebuild.py`
- `logs/`
- `mvnw`

## Execução (Git Bash)
```bash
chmod +x *.sh mvnw
./run-teste-v30-strict.sh
```

Para ultra:
```bash
chmod +x *.sh mvnw
./run-teste-v30-ultra.sh
```

## Observações
- A V30 foi gerada como: **V29 + novas requisições**
- O bloco novo é: `🧩 216 - V30 HARD LIMITS ENFORCEMENT`
- Mantém toda a base da V29 e adiciona enforcement real para users/products
