Ajustes aplicados no V21 ultra runner:

1. Extração robusta do api code no worker chaos-race-worker-sale.sh.
2. Geração de três arquivos de resumo:
   - chaos-http-status-counts.txt
   - chaos-api-code-counts.txt
   - chaos-http-api-code-counts.txt
3. Resumo final no console com contagem por HTTP e por código de API.
4. Mapeamento ampliado para falhas de estoque (INSUFFICIENT_STOCK e aliases).

Arquivos principais:
- run-teste-v21-inventory-ledger-double-spend-ultra.sh
- chaos-race-worker-sale.sh
