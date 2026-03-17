# V100 Heavy Data Population Grid

Suite de populacao massiva do banco via requisicoes HTTP reais.

O que a V100 popula:
- multiplas contas / tenants
- usuarios de control plane com roles variadas e permissions explicitas
- multiplos billings
- usuarios tenant com roles e permissions variadas
- categorias e subcategorias
- suppliers
- customers
- products
- inventory inbound por produto
- sales com quantidade variavel de itens por venda

## Arquivos
- `multitenancy001.postman_collection.v100.heavy-data-population-grid.json`
- `multitenancy001.local.postman_environment.v100.heavy-data-population-grid.json`
- `run-teste-v100-heavy-data-population-grid-strict.sh`
- `run-teste-v100-heavy-data-population-grid-ultra.sh`

## Como rodar
```bash
./run-teste-v100-heavy-data-population-grid-strict.sh
```

ou

```bash
./run-teste-v100-heavy-data-population-grid-ultra.sh
```

## Ajuste de volume
Edite o environment e altere os valores:
- tenant_count
- controlplane_user_count
- billing_count
- tenant_user_count
- category_count
- subcategory_count
- supplier_count
- customer_count
- product_count
- sales_count
- max_items_per_sale
- inventory_seed_qty
