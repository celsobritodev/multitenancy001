# V23.3 Strict corrigida

Conteúdo:
- `run-teste-v23.3-strict-hardened-corrigida.sh`

O que foi corrigido:
- mesmo modelo operacional da ultra;
- sempre libera a porta 8080;
- sempre reseta o banco;
- sempre sobe uma aplicação nova;
- não reaproveita app já rodando;
- valida token/schema/product/customer ao final;
- faz smoke final de `/api/tenant/me` e inventory.

Uso:
1. Coloque o script dentro da pasta `e2e/teste_v23.3/`
2. Dê permissão de execução:
   `chmod +x run-teste-v23.3-strict-hardened-corrigida.sh`
3. Execute:
   `./run-teste-v23.3-strict-hardened-corrigida.sh`
