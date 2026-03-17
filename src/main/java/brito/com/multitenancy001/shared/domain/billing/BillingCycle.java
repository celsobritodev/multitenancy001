package brito.com.multitenancy001.shared.domain.billing;

/**
 * Ciclo de cobrança associado ao pagamento/plano.
 *
 * <p>Este enum representa a cadência comercial do vínculo de billing,
 * permitindo distinguir upgrade, renovação e demais operações.</p>
 */
public enum BillingCycle {

    /**
     * Cobrança mensal.
     */
    MONTHLY,

    /**
     * Cobrança anual.
     */
    YEARLY,

    /**
     * Cobrança pontual/avulsa.
     */
    ONE_TIME
}