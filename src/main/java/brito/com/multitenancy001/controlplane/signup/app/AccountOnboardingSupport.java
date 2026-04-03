package brito.com.multitenancy001.controlplane.signup.app;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType;

/**
 * Componente de apoio para o onboarding de account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Centralizar helpers simples do fluxo.</li>
 *   <li>Construir details estruturados de auditoria.</li>
 *   <li>Encapsular falhas de provisioning com código tipado.</li>
 * </ul>
 */
@Component
public class AccountOnboardingSupport {

    /**
     * Dados normalizados do signup.
     *
     * @param displayName nome de exibição da conta
     * @param loginEmail email de login
     * @param taxCountryCode código do país fiscal
     * @param taxIdType tipo do documento fiscal
     * @param taxIdNumber número do documento fiscal
     * @param password senha validada
     */
    public record SignupData(
            String displayName,
            String loginEmail,
            String taxCountryCode,
            TaxIdType taxIdType,
            String taxIdNumber,
            String password
    ) {
    }

    /**
     * Exceção interna usada para propagar código de falha de provisioning.
     */
    public static class ProvisioningFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final ProvisioningFailureCode code;

        /**
         * Cria exceção de falha tipada de provisioning.
         *
         * @param code código da falha
         * @param cause causa original
         */
        public ProvisioningFailedException(ProvisioningFailureCode code, Throwable cause) {
            super(cause);
            this.code = code;
        }

        /**
         * Retorna o código de falha.
         *
         * @return código de falha
         */
        public ProvisioningFailureCode code() {
            return code;
        }
    }

    /**
     * Cria wrapper para falha de provisioning.
     *
     * @param code código da falha
     * @param exception exceção original
     * @return exceção encapsulada
     */
    public ProvisioningFailedException provisioningFailed(
            ProvisioningFailureCode code,
            Exception exception
    ) {
        return new ProvisioningFailedException(code, exception);
    }

    /**
     * Retorna mensagem segura e curta da causa.
     *
     * @param cause causa original
     * @return mensagem segura
     */
    public String safeMessage(Throwable cause) {
        if (cause == null) {
            return "unknown";
        }

        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }

        return message.trim();
    }

    /**
     * Monta details estruturados do onboarding.
     *
     * @param account account alvo
     * @param signupData dados normalizados
     * @param stage estágio lógico
     * @param code código de falha, quando houver
     * @param cause causa técnica/funcional, quando houver
     * @return mapa estruturado
     */
    public Map<String, Object> buildDetails(
            Account account,
            SignupData signupData,
            String stage,
            ProvisioningFailureCode code,
            Throwable cause
    ) {
        Map<String, Object> details = new LinkedHashMap<>();

        details.put("stage", stage);
        details.put("accountId", account != null ? account.getId() : null);
        details.put("tenantSchema", account != null ? account.getTenantSchema() : null);
        details.put("slug", account != null ? account.getSlug() : null);

        if (signupData != null) {
            details.put("displayName", signupData.displayName());
            details.put("loginEmail", signupData.loginEmail());
            details.put("taxCountryCode", signupData.taxCountryCode());
            details.put("taxIdType", signupData.taxIdType() != null ? signupData.taxIdType().name() : null);
            details.put("taxIdNumber", signupData.taxIdNumber());
        } else {
            details.put("displayName", null);
            details.put("loginEmail", null);
            details.put("taxCountryCode", null);
            details.put("taxIdType", null);
            details.put("taxIdNumber", null);
        }

        details.put("code", code != null ? code.name() : null);
        details.put("causeClass", cause != null ? cause.getClass().getName() : null);
        details.put("causeMessage", safeMessage(cause));

        return details;
    }
}