# teste_v25.3

V25.3 - Deterministic Chaos Engine patched.

## Evolução principal
- correlationId determinístico por worker/tentativa
- monotonic timestamp por tentativa e por worker
- retry tracing completo
- métricas de latência por worker/attempt no agregador

## Arquivos
- multitenancy001.postman_collection.v25.3.deterministic-chaos-engine-patched.json
- multitenancy001.local.postman_environment.v25.3.deterministic-chaos-engine-patched.json
- run-teste-v25.3-strict.sh
- run-teste-v25.3-ultra.sh
- chaos-race-worker-sale.sh
- chaos-race-aggregate.py
- cleanup.sh
