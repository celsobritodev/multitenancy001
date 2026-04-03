package brito.com.multitenancy001.controlplane.signup.app;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType;

/**
 * Componente de apoio para o onboarding de Account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Centralizar helpers simples do fluxo.</li>
 *   <li>Padronizar construção de details JSON de auditoria.</li>
 *   <li>Padronizar encapsulamento de falhas de provisioning.</li>
 * </ul>
 */
@Component
public class AccountOnboardingSupport {

    /**
     * Encapsula dados validados e normalizados do signup.
     *
     * @param displayName nome da empresa
     * @param loginEmail email de login
     * @param taxCountryCode país fiscal
     * @param taxIdType tipo do documento
     * @param taxIdNumber número do documento
     * @param password senha já validada
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
     * Exceção interna para propagar código de falha de provisioning.
     */
    public static class ProvisioningFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final ProvisioningFailureCode code;

        /**
         * Cria wrapper de falha de provisioning.
         *
         * @param code código da falha
         * @param cause causa original
         */
        public ProvisioningFailedException(ProvisioningFailureCode code, Throwable cause) {
            super(cause);
            this.code = code;
        }

        /**
         * Retorna código da falha.
         *
         * @return código da falha
         */
        public ProvisioningFailureCode code() {
            return code;
        }
    }

    /**
     * Cria wrapper de falha de provisioning.
     *
     * @param code código da falha
     * @param exception causa original
     * @return exceção encapsulada
     */
    public ProvisioningFailedException provisioningFailed(
            ProvisioningFailureCode code,
            Exception exception
    ) {
        return new ProvisioningFailedException(code, exception);
    }

    /**
     * Retorna mensagem segura da exceção.
     *
     * @param throwable exceção
     * @return mensagem segura
     */
    public String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        String message = throwable.getMessage();
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    /**
     * Monta details JSON simples para auditoria de provisioning.
     *
     * @param account account alvo
     * @param signupData dados do signup
     * @param stage estágio atual
     * @param code código de falha opcional
     * @param cause causa opcional
     * @return json em string
     */
    public String buildDetailsJson(
            Account account,
            SignupData signupData,
            String stage,
            ProvisioningFailureCode code,
            Throwable cause
    ) {
        String accountId = (account == null || account.getId() == null)
                ? null
                : String.valueOf(account.getId());

        String tenantSchema = account == null ? null : account.getTenantSchema();
        String slug = account == null ? null : account.getSlug();

        String causeClass = cause == null ? null : cause.getClass().getName();
        String causeMessage = cause == null ? null : safeMessage(cause);

        return "{"
                + "\"stage\":\"" + stage + "\""
                + ",\"accountId\":\"" + accountId + "\""
                + ",\"tenantSchema\":\"" + tenantSchema + "\""
                + ",\"slug\":\"" + slug + "\""
                + ",\"loginEmail\":\"" + (signupData == null ? null : signupData.loginEmail()) + "\""
                + ",\"code\":\"" + (code == null ? null : code.name()) + "\""
                + ",\"causeClass\":\"" + causeClass + "\""
                + ",\"causeMessage\":\"" + causeMessage + "\""
                + "}";
    }
}