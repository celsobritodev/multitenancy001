# V100.3 - MANY TENANTS DATA POPULATION GRID

Suite de população pesada via requisições HTTP, seguindo a linha da V100, com foco em **muitos tenants**.

## Objetivo

Popular o banco por endpoints já existentes, sem inventar contrato novo, ampliando a massa principalmente no eixo multi-tenant.

## Massa alvo desta versão

- tenants: 12
- controlplane users: 18
- billings: 36
- tenant users: 72
- categories: 72
- subcategories: 96
- suppliers: 60
- customers: 120
- products válidos: 96
- sales: 240

## Observações

- Mantém a mesma linha funcional da V100.2.
- Escala principalmente o número de tenants.
- Continua aceitando alguns `409 Conflict` esperados em billing.
- Continua tolerando alguns `400 Bad Request` controlados no grid de produtos, preservando o comportamento já observado como estável na V100.2.

## Arquivos

- `multitenancy001.postman_collection.v100.3.many-tenants.json`
- `multitenancy001.local.postman_environment.v100.3.many-tenants.json`
- `run-teste-v100.3-many-tenants-strict.sh`
- `run-teste-v100.3-many-tenants-ultra.sh`
- `cleanup.sh`

## Execução

```bash
chmod +x *.sh
./run-teste-v100.3-many-tenants-strict.sh
```

ou

```bash
chmod +x *.sh
./run-teste-v100.3-many-tenants-ultra.sh
```
