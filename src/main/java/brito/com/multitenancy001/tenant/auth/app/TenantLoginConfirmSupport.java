package brito.com.multitenancy001.tenant.auth.app;

import java.util.UUID;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * Support do fluxo de confirmação de login tenant.
 *
 * <p>Responsabilidade:</p>
 * <ul>
 *   <li>Helpers pequenos e determinísticos do fluxo CONFIRM.</li>
 * </ul>
 */
@Component
public class TenantLoginConfirmSupport {

    /**
     * Faz parse e valida o challengeId recebido externamente.
     *
     * @param rawChallengeId valor bruto recebido na request
     * @return UUID validado
     */
    public UUID parseChallengeId(String rawChallengeId) {
        try {
            return UUID.fromString(rawChallengeId);
        } catch (Exception ex) {
            throw new ApiException(ApiErrorCode.INVALID_CHALLENGE, "challengeId inválido");
        }
    }
}