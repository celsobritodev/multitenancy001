package brito.com.multitenancy001.tenant.auth.app;

import java.util.UUID;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * Componente de apoio para o fluxo de confirmação de login de tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Converter challengeId de string para UUID.</li>
 *   <li>Centralizar payloads JSON simples de falha.</li>
 * </ul>
 */
@Component
public class TenantLoginConfirmSupport {

    /**
     * Converte challengeId textual para UUID válido.
     *
     * @param rawChallengeId challengeId recebido no request
     * @return UUID parseado
     */
    public UUID parseChallengeId(String rawChallengeId) {
        try {
            return UUID.fromString(rawChallengeId);
        } catch (Exception ex) {
            throw new ApiException(ApiErrorCode.INVALID_CHALLENGE, "challengeId inválido", 400);
        }
    }

    /**
     * Monta JSON simples de falha do fluxo.
     *
     * @param reason motivo lógico da falha
     * @return payload json
     */
    public String failureJson(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }
}