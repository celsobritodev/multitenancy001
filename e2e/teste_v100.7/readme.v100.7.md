# V100.7 HEAVY DATA POPULATION GRID HARDENED

Esta versão segue seu padrão incremental:

- **V100.7 = V100.6 + hardening real**
- mantém toda a suíte anterior
- corrige o problema de 400 recorrente no `04.07 create product grid`

## Hardening aplicado

### 1. Mapeamento válido category -> subcategory
A V100 anterior guardava as subcategorias em lista plana por tenant.
Na prática, o product grid combinava:
- `categoryId = cats[idx % cats.length]`
- `subcategoryId = subs[idx % subs.length]`

Isso fazia alguns produtos usarem subcategory de outra category, gerando 400.

A V100.7 agora persiste:
- `subcategories_by_category_by_tenant`

E o product grid só escolhe subcategory pertencente à category atual.

### 2. Assertion NO-400 real no product grid
O passo `04.07 create product grid` agora aceita apenas:
- 200
- 201

Ou seja, se voltar 400, a suíte falha de forma explícita.

### 3. Compatibilidade
Foram mantidos:
- nomes legados da collection/environment
- nomes versionados V100.7
- runners legacy e versionados

## Como rodar
```bash
cd ~/eclipse-workspace/multitenancy001/e2e
unzip teste_v100.7_hardened_completa.zip
cd teste_v100.7
chmod +x *.sh
./run-teste-v100.7-enterprise-data-population-grid-hardened-strict.sh
```
