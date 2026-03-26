# Guia do Projeto: Sistema de Inteligência para Vendedores do Mercado Livre

## 1. O que é este projeto?

Imagine que você é um vendedor no Mercado Livre. Você tem uma loja, centenas de produtos, anúncios, estoque, vendas, reclamações... e precisa acompanhar tudo isso para não perder dinheiro.

Este projeto é um **assistente inteligente** que fica de olho na sua loja 24 horas por dia, 7 dias por semana. Ele analisa automaticamente todos os dados da sua operação e te avisa quando algo precisa da sua atenção.

**Pense como um "norton utilities" para o seu negócio no Mercado Livre** - um sistema que escaneia sua conta e te mostra onde estão os problemas e as oportunidades.

---

## 2. O que o sistema faz por você?

### 2.1. Monitora sua Saúde Financeira

**💰 Produtos com margem baixa**
- O sistema sabe o quanto você pagou por cada produto (custo)
- Ele calcula quanto você realmente ganha depois de descontar frete, taxas do ML, impostos e embalagem
- **Se a margem estiver abaixo de 30%, você é avisado:** "Atenção! Produto X está com margem muito baixa. Pode ser hora de reajustar o preço."

**📊 Curva ABC (O que realmente vende)**
- Descobre quais são seus 20% de produtos que geram 80% do faturamento (os "queridinhos")
- Identifica os 50% de produtos que mal vendem (os "pesos mortos" no estoque)
- **Te mostra visualmente onde focar seu dinheiro e esforço**

**🎯 Diagrama de Pareto (Onde está o problema)**
- Se você tem muitas devoluções ou reclamações, o sistema descobre quais são os 20% dos produtos responsáveis por 80% dos problemas
- **Te ajuda a resolver primeiro o que mais impacta negativamente sua loja**

---

### 2.2. Controla seu Estoque

**📦 Estoque parado**
- Produtos que estão no seu estoque há muito tempo sem vender
- **Alerta:** "Este produto está parado há 60 dias. Que tal fazer uma promoção para girar esse estoque?"

**🏭 Produtos no Full (armazém do ML)**
- Seus produtos estão no armazém do Mercado Livre, mas não estão vendendo
- **Alerta:** "Atenção! Produto X está no Full há 45 dias sem vender. Em 15 dias você começará a pagar taxa de armazenamento."

**⚠️ Risco de descarte**
- Produtos no Full que estão prestes a ser descartados por falta de venda
- **Alerta URGENTE:** "Produto Y será descartado em 10 dias se não for vendido. Crie uma oferta agressiva agora!"

**🔄 Transferência inteligente de estoque**
- Você tem o mesmo produto em dois anúncios diferentes
- Um anúncio vende muito, o outro quase nada
- **Sugestão:** "Que tal transferir o estoque do anúncio que vende pouco para o que vende muito? Assim você aproveita o bom ranking do anúncio que está bombando!"

---

### 2.3. Acompanha o Desempenho de Vendas

**📈 Produto vendendo muito (risco de faltar)**
- Identifica quando um produto começa a vender muito mais do que o normal
- Calcula quanto tempo seu estoque vai durar
- **Alerta:** "Produto Z está vendendo muito! Com o estoque atual, você tem só mais 5 dias. Hora de reabastecer ou ajustar o preço."

**📉 Produto vendendo pouco**
- Detecta quando um produto tem vendas muito espaçadas
- **Alerta:** "Produto W está vendendo devagar. Talvez seja hora de melhorar o anúncio ou dar um pequeno desconto."

---

### 2.4. Otimiza seus Anúncios

**🏷️ Produto sem promoção**
- Identifica produtos com preço alto que não estão em nenhuma campanha promocional
- **Sugestão:** "Que tal colocar este produto em promoção? Isso pode aumentar suas vendas."

**📱 Preço muito baixo em produto que vende muito**
- Se um produto está vendendo muito, talvez você possa aumentar o preço sem perder vendas
- **Sugestão:** "Este produto está com preço abaixo do mercado. Um pequeno aumento pode aumentar seu lucro."

**📢 Anúncio sem estoque**
- Você esqueceu de pausar um anúncio quando o estoque acabou
- **Alerta:** "Este anúncio está ativo mas sem estoque. Pause para não perder reputação."

---

### 2.5. Cuida da sua Reputação

**💬 Perguntas não respondidas**
- O sistema monitora perguntas dos clientes que você ainda não respondeu
- **Alerta com escalonamento:**
  - *1º aviso:* "Nova pergunta sobre produto X"
  - *2º aviso:* "Pergunta sem resposta há 3 dias"
  - *3º aviso:* "Pergunta sem resposta há 6 dias - prazo final!"

**⚠️ Reclamações abertas**
- Reclamações que estão prestes a expirar sem solução
- **Alerta URGENTE:** "Reclamação sobre pedido Y expira em 24 horas. Responda imediatamente!"

---

### 2.6. Análise de Marketing (Futuro)

**📢 Anúncios pagos (ML Ads)**
- Monitora campanhas de anúncios pagos
- Calcula quanto você está gastando vs. quanto está ganhando
- **Sugestão:** "Campanha X está com baixo retorno. Que tal pausar e investir mais na campanha Y que está bombando?"

---

## 3. Como o sistema te ajuda no dia a dia?

### Cenário 1: Sexta-feira de manhã

Você abre o sistema e vê:

- **3 alertas críticos:** Produtos no Full que vão começar a pagar taxa em 3 dias
- **5 alertas de oportunidade:** Produtos com margem baixa que podem ter preço ajustado
- **1 sugestão:** Transferir estoque de um anúncio fraco para um anúncio forte

Você gasta 15 minutos resolvendo os alertas críticos e sai para o fim de semana tranquilo.

### Cenário 2: Segunda-feira de manhã

Você abre o sistema e vê:

- **Curva ABC atualizada:** Descobre que 3 produtos que você não dava atenção estão no topo das vendas
- **Pareto de reclamações:** Um único produto é responsável por 70% das suas devoluções
- **Sugestão de preço:** 2 produtos que vendem muito podem ter o preço aumentado sem risco

Você realoca estoque, ajusta preços e resolve o problema do produto problemático. Seu lucro aumenta 15% no próximo mês.

---

## 4. Quais informações o sistema precisa?

Para fazer tudo isso, o sistema precisa de:

### Do seu cadastro
- Preço de custo de cada produto
- Categorias e fornecedores

### Do Mercado Livre (via integração)
- Seus anúncios e preços
- Suas vendas e faturamento
- Seu estoque no Full (armazém)
- Perguntas e reclamações
- Campanhas de anúncios pagos

### De você (configuração)
- Qual é a margem mínima aceitável (ex: 30%)
- Quanto tempo sem venda significa "estoque parado" (ex: 60 dias)
- Qual intervalo entre vendas significa "poucas vendas" (ex: 10 dias)
- Custos extras: frete médio, impostos, embalagem

---

## 5. Por que este sistema é diferente?

### É proativo, não reativo
Em vez de você ter que ficar olhando relatórios e tentando descobrir o que está errado, o sistema **te avisa** quando algo precisa da sua atenção.

### É inteligente
Não são alertas genéricos. O sistema calcula margens reais, considera todos os custos, analisa padrões de venda e te dá **sugestões acionáveis**.

### É personalizável
Você define suas próprias regras: o que é "margem baixa", o que é "venda demais", o que é "estoque parado". Cada loja é diferente.

### É completo
Cobre desde a saúde financeira (margens, custos) até operação (estoque, Full) e atendimento (perguntas, reclamações).

---

## 6. Fases de implementação

### Fase 1 (Agora) - O básico funcionando
Com os dados que você já tem (preço de custo, vendas, estoque), o sistema já consegue:

- Calcular margens de lucro
- Identificar produtos que vendem muito ou pouco
- Detectar estoque parado
- Gerar Curva ABC e Pareto

**Valor entregue:** Você já começa a ter visibilidade sobre o que realmente está acontecendo na sua loja.

### Fase 2 (Próxima) - Conectado ao Mercado Livre
O sistema passa a buscar dados diretamente do Mercado Livre:

- Preços reais dos anúncios
- Promoções ativas
- Estoque no Full
- Perguntas e reclamações
- Vendas em tempo real

**Valor entregue:** Alertas mais precisos e novos diagnósticos (taxas de armazenamento, risco de descarte, perguntas não respondidas).

### Fase 3 (Futuro) - Ações inteligentes
O sistema não só alerta, mas sugere e até executa ações:

- "Clique aqui para ajustar o preço"
- "Clique aqui para criar uma promoção"
- "Sugiro transferir X unidades do anúncio A para o anúncio B"
- "Campanha de anúncios pagos com baixo retorno - deseja pausar?"

**Valor entregue:** Você economiza tempo e toma decisões melhores, mais rápido.

---

## 7. Resumo: O que você ganha?

| Área | Benefício |
|------|-----------|
| **Financeiro** | Não vende no prejuízo, maximiza margens, identifica produtos mais lucrativos |
| **Estoque** | Não paga taxas desnecessárias, não perde produtos por descarte, gira estoque parado |
| **Vendas** | Aproveita produtos que estão vendendo bem, corrige anúncios fracos |
| **Reputação** | Não perde prazos de resposta, resolve reclamações antes do prazo |
| **Tempo** | Não precisa ficar olhando relatórios, o sistema te avisa só quando precisa |

---

## 8. Para quem é este sistema?

- **Pequenos vendedores:** Para não perder dinheiro com taxas e estoque parado
- **Médios vendedores:** Para escalar a operação sem aumentar equipe
- **Grandes vendedores:** Para identificar rapidamente problemas que podem custar milhares de reais

---

## 9. Conclusão

Este projeto é um **co-piloto inteligente** para o seu negócio no Mercado Livre. Ele fica de olho em tudo que acontece, te avisa do que precisa ser feito e sugere o melhor caminho para você vender mais e gastar menos.

Em vez de você correr atrás dos problemas, o sistema traz os problemas até você - com a solução já sugerida.

**É como ter um gerente de loja dedicado, trabalhando 24 horas por dia, 7 dias por semana, analisando cada detalhe da sua operação.**

---

**Documento criado em:** 26 de março de 2026
**Versão:** 1.0