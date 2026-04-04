package brito.com.multitenancy001.controlplane.signup.app;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * Validador semântico do fluxo de signup de Account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar obrigatoriedade dos campos do signup.</li>
 *   <li>Normalizar e validar email.</li>
 *   <li>Validar senha e confirmação.</li>
 *   <li>Montar o DTO interno normalizado do onboarding.</li>
 * </ul>
 */
@Service
public class AccountOnboardingValidator {

    private static final String DEFAULT_TAX_COUNTRY_CODE = "BR";

    /**
     * Valida e normaliza o comando de signup.
     *
     * @param signupCommand comando bruto
     * @return dados normalizados para o onboarding
     */
    public AccountOnboardingHelper.SignupData validateAndNormalize(SignupCommand signupCommand) {
        if (signupCommand == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Requisição inválida", 400);
        }

        String displayName = safeTrim(signupCommand.displayName());
        if (!StringUtils.hasText(displayName)) {
            throw new ApiException(ApiErrorCode.INVALID_COMPANY_NAME, "Nome da empresa é obrigatório", 400);
        }

        String loginEmail = EmailNormalizer.normalizeOrNull(signupCommand.loginEmail());
        if (!StringUtils.hasText(loginEmail)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório", 400);
        }

        if (!looksLikeEmail(loginEmail)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email inválido", 400);
        }

        TaxIdType taxIdType = signupCommand.taxIdType();
        if (taxIdType == null) {
            throw new ApiException(ApiErrorCode.INVALID_COMPANY_DOC_TYPE, "Tipo de documento é obrigatório", 400);
        }

        String taxIdNumber = safeTrim(signupCommand.taxIdNumber());
        if (!StringUtils.hasText(taxIdNumber)) {
            throw new ApiException(ApiErrorCode.INVALID_COMPANY_DOC_NUMBER, "Número do documento é obrigatório", 400);
        }

        String password = safeTrim(signupCommand.password());
        if (!StringUtils.hasText(password)) {
            throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatória", 400);
        }

        String confirmPassword = safeTrim(signupCommand.confirmPassword());
        if (!StringUtils.hasText(confirmPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_CONFIRM_PASSWORD, "Confirmação de senha é obrigatória", 400);
        }

        if (!password.equals(confirmPassword)) {
            throw new ApiException(ApiErrorCode.PASSWORD_MISMATCH, "Senha e confirmação não conferem", 400);
        }

        return new AccountOnboardingHelper.SignupData(
                displayName,
                loginEmail,
                DEFAULT_TAX_COUNTRY_CODE,
                taxIdType,
                taxIdNumber,
                password
        );
    }

    /**
     * Remove espaços laterais de string opcional.
     *
     * @param value valor bruto
     * @return valor tratado
     */
    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Validação simples de formato de email.
     *
     * @param email email normalizado
     * @return true quando parece email válido
     */
    private boolean looksLikeEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}