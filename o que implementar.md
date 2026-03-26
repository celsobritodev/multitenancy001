# Roadmap de Implementação: Mercado Livre Intelligence Suite

## 1. Visão Geral

Este documento consolida o plano de implementação das funcionalidades de inteligência de negócio e integração com o Mercado Livre para o projeto `multitenancy001`.

A arquitetura multi-tenant do projeto (schema-per-tenant) é a base perfeita para construir um SaaS de análise e gestão de vendas no Mercado Livre. O objetivo é transformar dados brutos de vendas, estoque e anúncios em insights acionáveis para o lojista.

---

## 2. Consolidação das Funcionalidades (Diagnósticos)

A tabela abaixo consolida as 13 funcionalidades identificadas nos textos, descrevendo a lógica de implementação e a ação sugerida para cada diagnóstico.

| ID | Diagnóstico / Análise | Lógica de Implementação no Projeto | Objetivo e Ações Sugeridas |
| :--- | :--- | :--- | :--- |
| **D1** | **Margem Insuficiente (< 30%)** | Cruza `price` (do anúncio/ML) com `costPrice` (do seu `Product`), aplicando as taxas configuradas por tenant (frete, ML, impostos). A margem é recalculada para cada produto. | **Ação**: Sugerir aumento de preço, revisão de custos, ou descontinuação do anúncio. |
| **D2** | **Taxas de Armazenagem (Full)** | Monitora tempo de estoque no Full (`inbound_date` via API) e volume. Se o estoque não girar dentro do período isento (ex: 60 dias), gera alerta. | **Ação**: Sugerir remoção do Full, promoção para queima de estoque, ou ajuste de preço. |
| **D3** | **Risco de Descarte (Full)** | Cruza alerta do ML (webhook/API) com produtos em Full há muito tempo sem venda. | **Ação**: Sugerir retirada imediata do Full ou criação de oferta agressiva. |
| **D4** | **Poucas Vendas (Anúncio Abaixo da Média)** | Analisa o intervalo entre vendas (`sale_date`). Se o intervalo médio for maior que o limite configurado pelo tenant (ex: 10 dias), gera alerta. | **Ação**: Sugerir melhoria de título/fotos, pequeno desconto, ou revisão de anúncio. |
| **D5** | **Muitas Vendas (Risco de Ruptura)** | Identifica aceleração de vendas e cruza com estoque disponível. Calcula "dias de estoque" (stock / vendas_diarias_media). | **Ação**: Sugerir aumento de preço, reabastecimento rápido, ou verificação de fornecedor. |
| **D6** | **Anúncios Iguais (SKU) com Desempenho Divergente** | Mapeia anúncios ML para um mesmo `SKU`. Compara o ritmo de vendas de cada um. | **Ação**: Sugerir **mover estoque** do anúncio que vende pouco para o que vende muito (otimiza ranking). |
| **D7** | **Preço Alto / Sem Promoção** | Identifica produtos com preço alto (acima de um limiar) que não possuem uma `promotion_id` ativa via API. | **Ação**: Sugerir criar uma promoção para aumentar competitividade e visibilidade. |
| **D8** | **Estoque Parado (Sem Giro)** | Identifica produtos com `stock_quantity > 0` que não aparecem em `sale_items` há mais de X dias. | **Ação**: Sugerir descontos agressivos, criação de kits, ou realização de liquidação. |
| **D9** | **Estoque Zero em Anúncio Ativo** | Monitora anúncios ML com status "ativo" e `stock_quantity` = 0. | **Ação**: Sugerir pausar anúncio (evita perda de ranking e reputação) ou verificar estoque em outro anúncio. |
| **D10** | **Monitor de Perguntas (Reclamações)** | Puxa perguntas não respondidas e reclamações abertas via API/Webhook. | **Ação**: Alertar com escalonamento de tempo (abriu, metade do SLA, prazo final). |
| **D11** | **Curva ABC de Faturamento** | Processa `total_price` por SKU, ordena decrescente e aplica cálculo de percentual acumulado (80/15/5). | **Ação**: Direcionar foco de estoque e investimentos (Ads) para os produtos "Classe A". |
| **D12** | **Diagrama de Pareto (80/20)** | Representação visual que correlaciona causas (SKUs) e efeitos (Lucro ou Devoluções). | **Ação**: Foco cirúrgico em resolver os 20% de produtos que causam 80% dos problemas ou geram 80% do lucro. |
| **D13** | **Análise de Ads (Futuro)** | Puxa dados de campanhas de anúncios pagos (ML Ads). Cruza investimento vs. retorno. | **Ação**: Sugerir pausar campanhas com baixo ROI e aumentar investimento nas de alto retorno. |

---

## 3. Roadmap de Implementação por Fases

### Fase 0: A Fundação (Já Existe no Projeto)

*   **Multi-tenancy por Schema:** Cada tenant possui seu próprio esquema de banco de dados (`tenantSchema`), garantindo isolamento de dados.
*   **Segurança e Auditoria:** Sistema robusto de auditoria (`AuditInfo`) que rastreia criação, atualização e exclusão de todos os registros.
*   **Gestão de Assinaturas:** Controle de ciclo de vida de contas (provisionamento, trial, suspensão por pagamento, cancelamento).

### Fase 1: O Coração dos Insights (Sem Integração ML)

**Objetivo:** Entregar valor imediato usando dados já presentes no sistema (preço de custo, vendas, estoque). Validar os motores de cálculo e criar a base para os próximos passos.

#### 1.1. Criar a Entidade `tenant_configs`

Crie uma nova tabela no schema PUBLIC para armazenar configurações customizáveis por tenant.

```sql
-- Migration: V21__create_table_tenant_configs.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS tenant_configs (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id),
    
    -- Configurações Financeiras
    minimum_margin_percent DECIMAL(5,2) NOT NULL DEFAULT 30.00,
    avg_freight_cost DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    avg_ml_fee_percent DECIMAL(5,2) NOT NULL DEFAULT 12.00,
    avg_tax_percent DECIMAL(5,2) NOT NULL DEFAULT 5.00,
    
    -- Configurações de Vendas
    few_sales_interval_days INTEGER NOT NULL DEFAULT 10,
    many_sales_avg_daily_threshold INTEGER NOT NULL DEFAULT 10,
    stock_without_movement_days INTEGER NOT NULL DEFAULT 60,
    
    -- Configurações de Full
    full_storage_free_days INTEGER NOT NULL DEFAULT 60,
    full_storage_alert_days INTEGER NOT NULL DEFAULT 45,
    
    -- Configurações Gerais
    analysis_window_days INTEGER NOT NULL DEFAULT 30,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tenant_configs_account_id ON tenant_configs(account_id);