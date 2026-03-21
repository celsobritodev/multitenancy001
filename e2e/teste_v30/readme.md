# V30 HARD LIMITS ENFORCEMENT - PATCHED

Pacote gerado com ajuste pronto para copiar/colar no seu padrão de projeto E2E.

## O que foi ajustado

1. Substituição de `postman.setNextRequest(...)` por `pm.execution.setNextRequest(...)` nos fluxos da collection.
2. Hardening do loop de produtos:
   - `216.07 create product until hard limit`
   - `216.08 reread limits after product creation`
   - `216.09 verify products exhausted before overflow`
   - `216.10 create product above hard limit`
3. Hardening defensivo do loop de usuários:
   - `216.02 create tenant user until hard limit`
   - `216.03 reread limits after user creation`
4. Ajuste do retry de login em `00.03 tenant login` para remover dependência do `postman.setNextRequest(...)`.

## Efeito esperado

- O bloco 216 não deve mais entrar em loop infinito quando `remainingProducts` chegar a `0` ou ficar negativo.
- O fluxo deve parar corretamente e seguir para validação de overflow controlado.
- A collection fica compatível com o formato recomendado pelo Postman/Newman atual.

## Arquivos incluídos

- collection V30 patchada
- environment V30
- scripts `run-teste-v30-strict.sh` e `run-teste-v30-ultra.sh`
- `cleanup.sh`
- scripts auxiliares de chaos já presentes no pacote base

## Observação

Este pacote preserva o nome dos arquivos principais para facilitar sobrescrita direta no diretório da suíte.
