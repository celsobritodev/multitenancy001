package brito.com.multitenancy001.controlplane.accounts.app.subscription;

/**
 * Violação específica identificada no preview de mudança de plano.
 *
 * @param type tipo da violação
 * @param resource recurso afetado
 * @param currentValue valor atual
 * @param allowedValue valor permitido
 * @param message mensagem funcional
 */
public record PlanEligibilityViolation(
        PlanEligibilityViolationType type,
        String resource,
        long currentValue,
        long allowedValue,
        String message
) {
}