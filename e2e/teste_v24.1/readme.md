# V24.0 - Transactional Hardening Suite

Evolução incremental direta da V23.3.

## O que foi mantido
- 100% da base da V23.3 strict corrigida
- mesmo modelo determinístico de execução
- mesma linha ultra com chaos/load, rollback probe e reconciliação
- compatibilidade com Git Bash no Windows

## Novos blocos V24
- **28 - Invalid Status / Transition Probes**
- **29 - Replay / Idempotency Probes**
- **30 - Chained Sale + Adjustment Hardening**
- **31 - Aggressive Stock Rules / Oversell Retries**
- **32 - Expanded Final Reconciliation**

## Arquivos principais
- `multitenancy001.postman_collection.v24.0.transactional-hardening.json`
- `multitenancy001.local.postman_environment.v24.0.transactional-hardening.json`
- `run-teste-v24-strict.sh`
- `run-teste-v24-ultra.sh`

## Execução
```bash
cd ~/eclipse-workspace/multitenancy001/e2e/teste_v24
./run-teste-v24-strict.sh
```

ou

```bash
cd ~/eclipse-workspace/multitenancy001/e2e/teste_v24
./run-teste-v24-ultra.sh
```

## Observação
A V24 mantém a filosofia: **não refatorar a suíte do zero**. Ela parte da V23.3 aprovada e adiciona probes de hardening transacional usando os endpoints já expostos na linha atual.


## Patch V24.1
- Ajustado o teste **30.04 Manual outbound after chained sale** para aceitar erro de regra controlado (400/409) quando o outbound manual pós-cadeia for bloqueado pelo domínio.
