# V31 SUBSCRIPTION LIFECYCLE

V31 gerada a partir da V30 real anexada.

## Regra aplicada
- V31 = V30 + bloco 220
- nenhum bloco anterior foi removido

## Bloco novo
- 220.00 init lifecycle vars
- 220.01 limits before lifecycle
- 220.02 preview downgrade to PRO
- 220.03 change-plan downgrade to PRO
- 220.04 reread after downgrade
- 220.05 preview re-upgrade to ENTERPRISE
- 220.06 change-plan re-upgrade to ENTERPRISE
- 220.07 reread after re-upgrade
- 220.08 lifecycle cycle decision
- 220.09 final lifecycle consistency

## Execução
```bash
chmod +x *.sh
./run-teste-v31-strict.sh
./run-teste-v31-ultra.sh
```

## ULTRA com mais ciclos
```bash
export V31_ULTRA_CYCLES=5
./run-teste-v31-ultra.sh
```
