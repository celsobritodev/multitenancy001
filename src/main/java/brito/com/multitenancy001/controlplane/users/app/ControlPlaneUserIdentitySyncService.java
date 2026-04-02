package brito.com.multitenancy001.controlplane.users.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pela sincronização de identidade de login
 * dos usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Garantir identidade após criação e restauração.</li>
 *   <li>Mover identidade após alteração de email.</li>
 *   <li>Remover identidade após soft delete.</li>
 * </ul>
 *
 * <p>Observação:</p>
 * <ul>
 *   <li>As operações de ensure e move são críticas e falham a transação lógica.</li>
 *   <li>A exclusão é tratada como best-effort, preservando o comportamento atual.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserIdentitySyncService {

    private final LoginIdentityService loginIdentityService;

    /**
     * Garante identidade de login do usuário do Control Plane.
     *
     * @param email email do usuário
     * @param userId id do usuário
     * @param operation nome da operação de origem
     */
    public void ensureControlPlaneIdentityNow(String email, Long userId, String operation) {
        log.info("ensureControlPlaneIdentityNow INICIANDO | email={} userId={} op={}", email, userId, operation);
        try {
            loginIdentityService.ensureControlPlaneIdentity(email, userId);
            log.info("✅ ensureControlPlaneIdentityNow CONCLUÍDO | email={} userId={}", email, userId);
        } catch (Exception ex) {
            log.error("❌ ensureControlPlaneIdentityNow FALHOU | email={} userId={} | erro={}",
                    email,
                    userId,
                    ex.getMessage(),
                    ex);
            throw new ApiException(
                    ApiErrorCode.INTERNAL_ERROR,
                    "Falha ao garantir identidade de login para usuário do Control Plane",
                    500
            );
        }
    }

    /**
     * Move identidade de login do usuário do Control Plane após troca de email.
     *
     * @param userId id do usuário
     * @param newEmail novo email
     * @param operation nome da operação de origem
     */
    public void moveControlPlaneIdentityNow(Long userId, String newEmail, String operation) {
        log.info("moveControlPlaneIdentityNow INICIANDO | userId={} newEmail={} op={}", userId, newEmail, operation);
        try {
            loginIdentityService.moveControlPlaneIdentity(userId, newEmail);
            log.info("✅ moveControlPlaneIdentityNow CONCLUÍDO | userId={} newEmail={}", userId, newEmail);
        } catch (Exception ex) {
            log.error("❌ moveControlPlaneIdentityNow FALHOU | userId={} newEmail={} | erro={}",
                    userId,
                    newEmail,
                    ex.getMessage(),
                    ex);
            throw new ApiException(
                    ApiErrorCode.INTERNAL_ERROR,
                    "Falha ao mover identidade de login",
                    500
            );
        }
    }

    /**
     * Remove identidade de login do usuário do Control Plane.
     *
     * <p>Operação best-effort.</p>
     *
     * @param userId id do usuário
     * @param operation nome da operação de origem
     */
    public void deleteControlPlaneIdentityNow(Long userId, String operation) {
        log.info("deleteControlPlaneIdentityNow INICIANDO | userId={} op={}", userId, operation);
        try {
            loginIdentityService.deleteControlPlaneIdentityByUserId(userId);
            log.info("✅ deleteControlPlaneIdentityNow CONCLUÍDO | userId={}", userId);
        } catch (Exception ex) {
            log.error("❌ deleteControlPlaneIdentityNow FALHOU (best-effort) | userId={} | erro={}",
                    userId,
                    ex.getMessage(),
                    ex);
        }
    }
}