package brito.com.multitenancy001.tenant.sales.domain;

/**
 * Status possíveis de uma venda no contexto TENANT.
 *
 * <p>Convenção operacional atual:</p>
 * <ul>
 *   <li>{@link #DRAFT}: rascunho. Não impacta estoque.</li>
 *   <li>{@link #OPEN}: venda aberta/em andamento. Impacta estoque.</li>
 *   <li>{@link #CONFIRMED}: venda confirmada. Impacta estoque.</li>
 *   <li>{@link #PAID}: venda paga. Impacta estoque.</li>
 *   <li>{@link #CANCELLED}: venda cancelada. Não impacta estoque.</li>
 * </ul>
 */
public enum SaleStatus {

    /**
     * Venda em rascunho.
     */
    DRAFT,

    /**
     * Venda aberta.
     */
    OPEN,

    /**
     * Venda confirmada.
     */
    CONFIRMED,

    /**
     * Venda paga.
     */
    PAID,

    /**
     * Venda cancelada.
     */
    CANCELLED
}