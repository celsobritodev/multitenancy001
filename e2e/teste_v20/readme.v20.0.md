# V20.0 - CHAOS RACE TEST ENTERPRISE STRICT SUITE

Extensão da linha V17.1.2, mantendo 100% compatibilidade funcional com a suíte validada.

## Escopo da v20.0

- mantém toda a collection da V17.1.2
- adiciona `📦 90 - SECURITY TESTS`
- adiciona `📦 91 - CHAOS RACE PREP`
- adiciona `📦 92 - CHAOS RACE VERIFY`
- adiciona `📦 93 - CHAOS ROLLBACK PROBE`
- runner ultra dispara **100 vendas simultâneas** do mesmo produto
- injeta **jitter**
- injeta **delay**
- executa **retry**
- roda **rollback probe**
- valida inventory antes/depois
- valida movements antes/depois
- garante regra explícita: **estoque não pode ficar negativo**

## Arquivos

- `multitenancy001.postman_collection.v20.0.chaos-race-enterprise.strict.json`
- `multitenancy001.local.postman_environment.v20.0.chaos-race-enterprise.strict.json`
- `run-teste-v20-chaos-race-enterprise.sh`
- `run-teste-v20-chaos-race-enterprise-ultra.sh`
- `chaos-race-worker-sale.sh`
- `cleanup.sh`
- `assets/v20-chaos-race-flow.png`

## Execução

```bash
./run-teste-v20-chaos-race-enterprise.sh
```

```bash
./run-teste-v20-chaos-race-enterprise-ultra.sh
```

## Variáveis principais de chaos

- `chaos_race_parallelism=100`
- `chaos_race_attempt_count=100`
- `chaos_race_max_jitter_ms=250`
- `chaos_race_base_delay_ms=20`
- `chaos_race_retry_count=2`

## Regras validadas

- spoofing / IDOR rejeitados
- inventory pós-race não pode ficar negativo
- movements pós-race devem manter ou incrementar histórico
- rollback probe não pode corromper inventory
