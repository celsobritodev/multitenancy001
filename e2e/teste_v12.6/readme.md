# Testes E2E - v12.6 (Customers + Sales)

Esta suite de testes foi criada para validar o módulo **customers** e sua integração com **sales**, além de testar o isolamento entre tenants.

## 🎯 Escopo

- CRUD completo de customers
- Busca por nome (search)
- Associação de customers com sales
- Isolamento de tenants (tenant1 não pode acessar customers do tenant2)

## 📁 Estrutura

e2e/teste_v12_6/
├── cleanup.sh # Limpeza de arquivos temporários
├── multitenancy001.local.postman_environment.v12.6.json # Environment (v12.6)
├── multitenancy001.postman_collection.v12.6.enterprise.json # Collection (v12.6)
├── run-teste-v12.6-simples.sh # Runner simples
├── run-teste-v12.6-ultra.sh # Runner ultra (verbose + retry)
└── readme.md # Este arquivo



## 🚀 Como executar

### Versão simples:
```bash
cd e2e/teste_v12_6
./run-teste-v12.6-simples.sh


### Versão detalhada:
cd e2e/teste_v12_6
./run-teste-v12.6-ultra.sh

Opções do runner ultra:
./run-teste-v12.6-ultra.sh --help
./run-teste-v12.6-ultra.sh --debug
./run-teste-v12.6-ultra.sh --timeout 300



