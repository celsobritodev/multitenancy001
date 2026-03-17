package brito.com.multitenancy001.controlplane.accounts.api.subscription.dto;

/**
 * DTO de violação de elegibilidade de mudança de plano.
 *
 * @param type tipo da violação
 * @param resource recurso afetado
 * @param currentValue valor atual
 * @param allowedValue valor permitido
 * @param message mensagem funcional
 */
public record AccountPlanViolationResponse(
        String type,
        String resource,
        long currentValue,
        long allowedValue,
        String message
) {
}