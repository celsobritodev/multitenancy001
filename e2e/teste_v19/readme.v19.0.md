# V19.0 - REAL RACE ENTERPRISE STRICT SUITE

Extensão da linha V17.1.2, mantendo 100% compatibilidade funcional com a suíte validada.

## Ajustes de entrega na v19.0

- mantém toda a collection da V17.1.2
- adiciona folder 90 - SECURITY TESTS
- adiciona folder 91 - REAL RACE PREP
- adiciona folder 92 - REAL RACE VERIFY
- runner ultra dispara 20 a 50 vendas simultâneas reais fora do Postman
- valida inventory antes/depois
- valida movements antes/depois
- regra explícita: estoque não pode ficar negativo
- mantém padrão visual com cores nos scripts
- inclui imagem de fluxo do race test

## Execução

```bash
./run-teste-v19-real-race-enterprise.sh
```

```bash
./run-teste-v19-real-race-enterprise-ultra.sh
```

## Escopo adicional da v19.0

- Tenant header spoofing
- Cross-tenant IDOR
- Real race test com paralelismo real
- Verificação explícita de inventory antes/depois
- Verificação explícita de movements antes/depois
- Garantia de non-negative stock
