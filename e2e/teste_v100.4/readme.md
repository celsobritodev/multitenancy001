# V100.4 - ENTERPRISE MANY TENANTS

Suíte derivada da V100.2, mantendo o contrato funcional validado e elevando a população para **20 tenants**.

## Perfil desta versão

- tenant_count = 20
- users_per_tenant = 6
- categories_per_tenant = 6
- subcategories_per_tenant = 8
- suppliers_per_tenant = 5
- customers_per_tenant = 10
- products_per_tenant = 12
- sales_per_tenant = 20
- billings_per_account = 3
- max_sale_items = 4

## Totais esperados por execução strict

- tenants: 20
- control plane users: 6
- billings: 60
- tenant users: 120
- categories: 120
- subcategories: 160
- suppliers: 100
- customers: 200
- products: 240
- sales: 400

## Arquivos

- `cleanup.sh`
- `multitenancy001.local.postman_environment.v100.4.enterprise-many-tenants.json`
- `multitenancy001.postman_collection.v100.4.enterprise-many-tenants.json`
- `run-teste-v100.4-enterprise-many-tenants-strict.sh`
- `run-teste-v100.4-enterprise-many-tenants-ultra.sh`

## Execução (Git Bash)

```bash
chmod +x *.sh
./run-teste-v100.4-enterprise-many-tenants-strict.sh
```

Para carga maior, rode:

```bash
chmod +x *.sh
./run-teste-v100.4-enterprise-many-tenants-ultra.sh
```
