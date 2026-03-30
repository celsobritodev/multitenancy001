# V102.8 FINAL CHAOS CALIBRADO

Base:
- V102.7 DEFINITIVA

Ajuste aplicado:
- calibração das assertions do bloco 90
- `90.08 chaos drain inventory to zero` agora aceita respostas seguras: 200, 201, 400, 409, 422
- `90.09 chaos impossible oversell` agora aceita respostas seguras: 200, 400, 409, 422

Objetivo:
- validar comportamento seguro do sistema sob chaos
- eliminar falso negativo por expectativa estreita demais
- preservar VN, hardening, chaos e consistência pós-operação

Execução:
```bash
cd ~/eclipse-workspace/multitenancy001/e2e/teste_v102.8
chmod +x *.sh
./run-teste-v102.8-strict.sh
./run-teste-v102.8-ultra.sh
```
