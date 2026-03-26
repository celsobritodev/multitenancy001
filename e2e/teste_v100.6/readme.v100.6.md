# V100.6 HEAVY DATA POPULATION GRID

Esta entrega preserva a base da V100 original e corrige o empacotamento/nomes dos artefatos.

## Arquivos principais
- `run-teste-v100.6-enterprise-data-population-no400-strict.sh`
- `run-teste-v100.6-enterprise-data-population-no400-ultra.sh`
- `multitenancy001.postman_collection.v100.heavy-data-population-grid.json`
- `multitenancy001.local.postman_environment.v100.heavy-data-population-grid.json`

## Compatibilidade
Também foram mantidos aliases versionados V100.6 para collection/environment.

## Como rodar no seu padrão
```bash
cd ~/eclipse-workspace/multitenancy001/e2e/teste_v100.6
chmod +x *.sh
./run-teste-v100.6-enterprise-data-population-no400-strict.sh
```

## Ajuste importante no runner
O runner agora procura a raiz do projeto em `../../` a partir da pasta da suíte, para encontrar:
- `mvnw`
- `.mvn/`
