# Testes V12 - Multitenancy001

Esta pasta contém os scripts para executar a bateria de testes da versão 12.

## 📋 Pré-requisitos

- Node.js (com Newman instalado: `npm install -g newman`)
- PostgreSQL (com usuário `postgres` e senha `admin`)
- Java 21
- Maven (ou usar o wrapper na raiz do projeto)

## 📁 Arquivos

- `multitenancy001.postman_collection.v12.0.enterprise.json` - Collection de testes
- `multitenancy001.local.postman_environment.v8.0.json` - Environment do Postman
- `run-teste-v12-ultra.sh` - Versão completa com logs e relatórios detalhados
- `run-teste-v12-simples.sh` - Versão simplificada
- `cleanup.sh` - Remove arquivos desnecessários

## 🚀 Como usar

### Opção 1: Versão Ultra (recomendada)
```bash
./run-teste-v12-ultra.sh