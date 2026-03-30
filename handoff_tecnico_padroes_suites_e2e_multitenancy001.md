# Handoff Técnico — Padrão de Trabalho com as Suítes E2E do projeto multitenancy001

## 1. Contexto do projeto

Projeto: **multitenancy001**  
Arquitetura: **DDD / layered**, **sem ports & adapters**  
Stack principal: **Java + Spring Boot + JPA + Flyway + Security**  
Modelo multi-tenant: **control plane no schema público + schema por tenant**  
Source of truth do banco: **Flyway**, com prática recorrente de **drop/recreate** do banco para execução limpa.  
Execução dos testes: **suítes E2E externas ao backend**, rodadas via **Newman** no **Git Bash**.  
Base funcional consolidada até o momento: **V32.9.1** como baseline estável.

## 2. Forma correta de trabalhar comigo neste projeto

Este projeto deve sempre seguir o seu padrão operacional e de entrega.

### 2.1. Como você executa as suítes

Você roda as suítes no **Git Bash**.

O fluxo esperado é sempre algo como:

```bash
chmod +x *.sh
./run-teste-vXX-strict.sh
./run-teste-vXX-ultra.sh
```

Ou seja:

- primeiro você garante permissão de execução com `chmod +x *.sh`
- depois roda a suíte **STRICT**
- depois roda a suíte **ULTRA**, quando quiser validar cenários mais fortes, loops, hardening, chaos ou carga incremental

### 2.2. STRICT e ULTRA

#### STRICT
A suíte **STRICT** é a execução principal de validação determinística.

Ela deve ser usada para:

- validar fluxo funcional principal
- validar contratos
- validar status esperados
- validar sequência estável
- detectar regressão rápida
- servir como baseline de confiança da versão

A STRICT precisa ser:

- limpa
- determinística
- sem improvisos
- sem dependência frágil de timing
- confiável para uso recorrente após reset de banco

#### ULTRA
A suíte **ULTRA** é a execução reforçada.

Ela deve ser usada para:

- hardening
- loops
- stress controlado
- expansão de cobertura
- cenários de limite
- cenários de carga incremental
- validações prolongadas
- cenários mais agressivos de negócio, inventory, sales, quota, lifecycle e chaos

A ULTRA **não substitui** a STRICT.  
Ela é uma camada adicional.

## 3. Regra principal de evolução das suítes

A regra obrigatória é:

```text
nova versão = versão anterior + novas requisições
```

Ou seja:

```text
V(N+1) = V(N) + novos testes / novas requisições / novas validações
```

### Isso significa na prática

Nunca:

- recomeçar a suíte do zero
- encolher a cobertura
- remover blocos já validados
- trocar uma suíte completa por uma versão menor
- perder testes antigos ao adicionar regras novas

Sempre:

- preservar tudo o que já foi validado
- adicionar novos blocos por cima da versão anterior
- manter compatibilidade com a linha histórica das suítes
- tratar cada versão nova como continuação da anterior

Exemplo conceitual:

- V33 deve ser baseada na V32.9.1 + novos blocos
- V34 deve ser baseada na V33 + novos blocos
- V35 deve ser baseada na V34 + novos blocos

## 4. Forma de entrega que você quer

### 4.1. Sempre em `.zip`
Quando for gerar nova suíte, você quer:

- **arquivo `.zip` pronto para download**
- tudo já organizado dentro da pasta da versão
- sem necessidade de montar manualmente
- sem precisar juntar arquivo por arquivo

### 4.2. Sempre código completo
Você **não quer snippets soltos**.

Você quer sempre:

- arquivos completos
- collection completa
- environment completo
- scripts completos
- sem “trechos para encaixar”
- sem “ajustes cirúrgicos”
- pronto para copiar, colar, baixar e executar

### 4.3. Sempre no seu padrão
A entrega deve respeitar seu padrão consolidado:

- pasta versionada
- scripts `.sh`
- `cleanup.sh`
- `logs/`
- STRICT
- ULTRA
- compatível com Git Bash
- pronta para rodar com Newman

## 5. Estrutura padrão das suítes

O padrão esperado das pastas é:

```text
e2e/
  teste_vXX/
    cleanup.sh
    logs/
    chaos-ledger-rebuild.py
    chaos-node-launch.sh
    chaos-race-aggregate.py
    chaos-race-worker-sale.sh
    multitenancy001.postman_collection.vXX.<nome>.json
    multitenancy001.local.postman_environment.vXX.<nome>.json
    run-teste-vXX-strict.sh
    run-teste-vXX-ultra.sh
    readme.md
```

### Observações importantes

- a pasta sempre deve seguir o padrão `teste_vXX`
- os nomes dos arquivos devem refletir a **mesma versão**
- runner STRICT e ULTRA devem apontar para os arquivos corretos da mesma versão
- `logs/` deve existir
- `cleanup.sh` deve existir
- quando houver chaos/hardening, os scripts auxiliares devem acompanhar a suíte

## 6. Convenção de nomes de arquivos

### 6.1. Collection
Padrão esperado:

```text
multitenancy001.postman_collection.vXX.<descricao>.json
```

Mesmo você usando **Newman**, o formato da collection continua sendo esse.

### 6.2. Environment
Padrão esperado:

```text
multitenancy001.local.postman_environment.vXX.<descricao>.json
```

### 6.3. Runner STRICT
Padrão esperado:

```text
run-teste-vXX-strict.sh
```

### 6.4. Runner ULTRA
Padrão esperado:

```text
run-teste-vXX-ultra.sh
```

### 6.5. Pasta da suíte
Padrão esperado:

```text
teste_vXX
```

## 7. Padrão operacional esperado dos runners

Os scripts de execução devem seguir sua forma de trabalho real.

### 7.1. O runner deve normalmente fazer

- verificar requisitos
- verificar / liberar porta 8080
- resetar banco
- criar link para `mvnw`
- preparar environment efetivo
- iniciar a aplicação
- aguardar boot com timeout
- checar `/actuator/health`
- executar Newman
- exportar relatórios/logs
- encerrar aplicação no final
- fazer cleanup de arquivos temporários

### 7.2. O runner deve ser amigável ao Git Bash
Você usa Git Bash, então os scripts devem ser compatíveis com esse ambiente.

Ou seja, evitar depender de coisas fora do padrão do seu setup quando não for necessário.

## 8. Como devo tratar novas regras de negócio

Quando você adicionar novas regras de negócio no projeto, a suíte nova deve:

- partir da última estável
- manter cobertura antiga
- adicionar novos blocos para a nova regra
- preservar STRICT e ULTRA
- continuar no mesmo padrão de nomes, pastas e scripts

### Regra operacional
Se entrar nova regra, **não se refaz a base**.  
A nova regra entra como expansão incremental da última suíte válida.

## 9. O que eu devo entender como obrigatório nas próximas entregas

Sempre assumir que você quer:

- **Newman**
- **Git Bash**
- `chmod +x *.sh`
- execução por script `.sh`
- suíte **STRICT**
- suíte **ULTRA**
- `.zip` pronto
- código completo
- collection completa
- environment completo
- runners completos
- estrutura de pastas pronta
- continuidade incremental da versão anterior

## 10. Padrão de qualidade esperado

A suíte entregue precisa ser:

- executável
- consistente
- versionada corretamente
- alinhada ao backend atual
- sem regressão de cobertura
- sem “mini suíte” recomeçada
- sem nomes quebrados
- sem referência cruzada para arquivos de outra versão
- com fluxo garantido

## 11. O que não fazer

Não entregar:

- snippet isolado
- arquivo avulso sem estrutura
- versão reduzida que perde cobertura
- runner apontando para nome errado
- collection incompleta
- environment incompleto
- suíte que depende de ajuste manual escondido
- nova versão menor que a versão anterior

## 12. Resumo executivo para referência futura

### Seu padrão consolidado é:

- usa **Git Bash**
- roda com **Newman**
- usa `chmod +x *.sh`
- executa por `run-teste-vXX-strict.sh` e `run-teste-vXX-ultra.sh`
- quer sempre **STRICT + ULTRA**
- quer sempre **`.zip` pronto para download**
- quer sempre **código completo**
- quer sempre **estrutura de pastas pronta**
- quer sempre **nova versão = versão anterior + novas requisições**
- não quer regressão
- não quer snippets
- não quer mini-suítes recomeçadas do zero

## 13. Conclusão

A forma correta de trabalhar com as suítes deste projeto é tratar a linha E2E como um ativo acumulativo e permanente.

A referência operacional é:

- **V32.9.1 como baseline estável**
- próximas versões sempre incrementais
- execução sempre via **Git Bash + Newman**
- entrega sempre em **`.zip`**
- arquivos sempre completos
- estrutura sempre padronizada

Esse documento deve ser usado como base fixa para qualquer novo chat, handoff técnico ou geração futura de suíte.
