# V32.3 FINAL LIMPA

Patch final de limpeza da V32.

## Correções aplicadas
- reset forte de vars de customer no 99.00
- 14.01 e 14.02 sempre recriam customer1/customer2 do ciclo atual
- 14.03 sincroniza customer1_id/customer2_id por listagem do ciclo
- 14.04, 14.05 e 14.06 reforçam sincronização do customer1_id atual
- 14.08 atualiza sempre o customer do ciclo atual
- 15.01 e 15.02 usam sempre customer1_id do ciclo atual
- blocos de tenant isolation passam a usar customer1_id atual

## Execução
chmod +x *.sh
./run-teste-v32.3-strict.sh
./run-teste-v32.3-ultra.sh
