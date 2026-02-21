package brito.com.multitenancy001.shared.api.compliance;

import java.lang.annotation.*;

/**
 * Marca controllers (ou métodos) que devem ser ignorados pelo ControllerComplianceVerifier.
 *
 * Uso típico:
 * - Endpoints técnicos/infra (health, actuator proxies, dev-only)
 * - Controllers legados em migração (temporário)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ControllerComplianceExempt {

    /**
     * Motivo textual para facilitar rastreio.
     */
    String value() default "";
}