# V29 - SUBSCRIPTION / BILLING BINDING SUITE

A V29 evolui a V28 e mantém integralmente os blocos anteriores.

## Adições da V29
- 212 - Tenant subscription limits + preview
- 213 - Tenant change-plan + billing binding
- 214 - Control Plane subscription admin
- 215 - Billing binding / access / final consistency

## Objetivo funcional
- validar limites do plano atual
- validar preview de mudança de plano
- validar upgrade via billing binding
- validar downgrade elegível / bloqueado
- validar metadados de pagamento associados ao plano
- validar leitura administrativa da assinatura no Control Plane

## Arquivos
- multitenancy001.postman_collection.v29.subscription-billing-binding.json
- multitenancy001.local.postman_environment.v29.subscription-billing-binding.json
- run-teste-v29-strict.sh
- run-teste-v29-ultra.sh

## Execução
```bash
./run-teste-v29-strict.sh
./run-teste-v29-ultra.sh
```

## Observações importantes
- a V29 é construída em cima da V28, sem remover os blocos já existentes
- o bootstrap/auth herdado da V28 foi preservado como base
- os novos blocos foram adicionados ao final da collection, mantendo o padrão material da suíte
- os scripts continuam resetando banco, subindo a aplicação, executando Newman e exportando artefatos em `logs/`

## Variáveis novas da V29
- subscription_target_plan
- subscription_target_plan_2
- subscription_blocked_downgrade_target
- subscription_eligible_downgrade_target
- subscription_billing_cycle
- subscription_payment_method
- subscription_payment_gateway
- subscription_currency_code
- subscription_amount_pro
- subscription_amount_enterprise
- subscription_price_pro
- subscription_price_enterprise

## Estratégia da suíte
1. roda toda a base estável da V28
2. consulta limites/uso da assinatura do tenant
3. faz preview de upgrade e de downgrade
4. executa change-plan via tenant com billing binding
5. executa leitura/administração via Control Plane
6. valida endpoints de billing query e consistência final
