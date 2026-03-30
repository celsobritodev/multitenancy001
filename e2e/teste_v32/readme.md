# teste_v32.9.1 FINAL

Evolui a V32.8 CLEAN sem perder cobertura.

## Ajustes principais
- customer1_id_locked vira a fonte de verdade do fluxo de customers
- 14.04 usa o id locked e valida ID exato
- 14.05 vira verificação auxiliar e não derruba o fluxo principal
- 14.06 sincroniza sem sobrescrever indevidamente o customer locked
- STRICT + ULTRA preservados no padrão Newman + Git Bash

## Execução
```bash
chmod +x *.sh
./run-teste-v32.9.1-strict.sh
./run-teste-v32.9.1-ultra.sh
```
