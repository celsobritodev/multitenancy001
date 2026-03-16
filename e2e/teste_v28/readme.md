# V28 - CANCEL / RETURN / REVERSAL GRID

A V28 evolui a V27 e mantém integralmente os blocos anteriores.

## Adições da V28
- 206 - Cancel flow hardening
- 207 - Return flow hardening
- 208 - Ledger reversal validation
- 209 - Cancel / return race probes
- 210 - Reversal drift detector
- 211 - Final cancel / return consistency

## Arquivos
- multitenancy001.postman_collection.v28.cancel-return-reversal-grid.json
- multitenancy001.local.postman_environment.v28.cancel-return-reversal-grid.json
- run-teste-v28-strict.sh
- run-teste-v28-ultra.sh

## Execução
```bash
./run-teste-v28-strict.sh
./run-teste-v28-ultra.sh
```

## Observação
A base estrutural segue o mesmo padrão operacional das versões anteriores: reset de banco, boot limpo, execução Newman, provas de consistência e artefatos em `logs/`.


Patch aplicado: corrigidas as assertions dos blocos 208.03 / 209.05 / 210.03 / 211.03 para usar baseline reconciliado mais recente do product2, evitando comparação indevida com o baseline antigo do multi-item.
