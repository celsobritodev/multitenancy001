package brito.com.multitenancy001.controlplane.accounts.app;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import lombok.RequiredArgsConstructor;

/**
 * Componente de apoio para helpers compartilhados do módulo de status de account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver dados do ator atual quando disponíveis.</li>
 *   <li>Proteger fluxos executados sem autenticação explícita, como jobs.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AccountStatusSupport {

    private final ControlPlaneRequestIdentityService controlPlaneRequestIdentityService;

    /**
     * Obtém o id do usuário executor atual ou null.
     *
     * @return id do ator ou null
     */
    public Long getCurrentActorUserIdOrNull() {
        return nullSafe(() -> controlPlaneRequestIdentityService.getCurrentUserId());
    }

    /**
     * Obtém o email do usuário executor atual ou null.
     *
     * @return email do ator ou null
     */
    public String getCurrentActorEmailOrNull() {
        return nullSafe(() -> controlPlaneRequestIdentityService.getCurrentEmail());
    }

    /**
     * Executa supplier protegendo contra exceções e retornando null.
     *
     * @param supplier supplier protegido
     * @return valor ou null
     * @param <T> tipo retornado
     */
    private static <T> T nullSafe(SupplierEx<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Supplier que pode lançar exceção.
     *
     * @param <T> tipo retornado
     */
    @FunctionalInterface
    private interface SupplierEx<T> {

        /**
         * Executa supplier.
         *
         * @return valor
         */
        T get();
    }
}