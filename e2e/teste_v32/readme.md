# V32.1 HARDENING REAL STABILIZED

Patch de estabilização da V32.

## O que foi corrigido
- customers agora usam variáveis determinísticas por execução
- customer 1 e customer 2 deixaram de depender de valores fixos no corpo dos requests
- criação de customer aceita 201/409 de forma controlada
- duplicate user probe do bloco 230 usa `shared_email` real do fluxo de ambiguidade
- runner STRICT agora grava `suite_strict_*.log`
- runner ULTRA agora grava `suite_ultra_*.log`
- ambos os runners capturam o exit code real do Newman

## Execução
chmod +x *.sh
./run-teste-v32.1-strict.sh
./run-teste-v32.1-ultra.sh
