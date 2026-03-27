# V32.4 SALES FIXED

Patch da V32 para corrigir o fluxo de sales.

## Correções aplicadas
- 14.05 usa `customer1_email` dinâmico do ciclo atual
- 15.01 agora envia payload compatível com o backend:
  - `saleDate`
  - `customerId`
  - `status`
  - `items[].productId`
  - `items[].productName`
  - `items[].quantity`
  - `items[].unitPrice`
- 15.03 exige `sale_with_customer_id` válido antes do GET da venda
- mantém ressincronização de customer por listagem/documento e isolamento por `customer1_id` atual

## Execução
chmod +x *.sh
./run-teste-v32.4-strict.sh
./run-teste-v32.4-ultra.sh
