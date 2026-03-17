package brito.com.multitenancy001.shared.domain.billing;

/**
 * Finalidade do pagamento.
 *
 * <p>Permite distinguir pagamentos operacionais comuns
 * de pagamentos com efeito direto sobre assinatura/plano.</p>
 */
public enum PaymentPurpose {

    /**
     * Upgrade de plano.
     */
    PLAN_UPGRADE,

    /**
     * Renovação do plano atual.
     */
    PLAN_RENEWAL,

    /**
     * Ajuste financeiro relacionado a plano.
     */
    PLAN_CHANGE_ADJUSTMENT,

    /**
     * Cobrança manual administrativa.
     */
    MANUAL_BILLING,

    /**
     * Outros casos.
     */
    OTHER
}