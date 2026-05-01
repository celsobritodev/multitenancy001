package brito.com.multitenancy001.tenant.auth.app;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.PublicAccountFinder;
import brito.com.multitenancy001.shared.persistence.publicschema.PublicAccountView;
import lombok.RequiredArgsConstructor;

/**
 * Componente de apoio para o fluxo de password reset do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Normalizar e validar inputs.</li>
 *   <li>Resolver account ativa por slug.</li>
 *   <li>Garantir disponibilidade de tenant schema.</li>
 *   <li>Validar claims mínimas do token de reset.</li>
 * </ul>
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem status HTTP hardcoded.</li>
 *   <li>Validação padronizada via ApiException.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenantPasswordResetHelper {

    private final PublicAccountFinder accountResolver;

    /**
     * Normaliza e valida slug obrigatório.
     *
     * @param rawSlug valor bruto
     * @return slug normalizado
     */
    public String normalizeSlugOrThrow(String rawSlug) {
        if (!StringUtils.hasText(rawSlug)) {
            throw new ApiException(ApiErrorCode.INVALID_SLUG, "Slug é obrigatório");
        }

        String slug = rawSlug.trim();
        if (slug.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_SLUG, "Slug é obrigatório");
        }

        return slug;
    }

    /**
     * Normaliza e valida email obrigatório.
     *
     * @param rawEmail valor bruto
     * @return email normalizado
     */
    public String normalizeEmailOrThrow(String rawEmail) {
        if (!StringUtils.hasText(rawEmail)) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "Email é obrigatório");
        }

        String normalized = EmailNormalizer.normalizeOrNull(rawEmail);
        if (!StringUtils.hasText(normalized)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email inválido");
        }

        return normalized;
    }

    /**
     * Normaliza e valida token obrigatório.
     *
     * @param rawToken valor bruto
     * @return token normalizado
     */
    public String normalizeTokenOrThrow(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new ApiException(ApiErrorCode.INVALID_TOKEN, "Token inválido");
        }

        String token = rawToken.trim();
        if (token.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_TOKEN, "Token inválido");
        }

        return token;
    }

    /**
     * Normaliza e valida nova senha obrigatória.
     *
     * @param rawPassword valor bruto
     * @return senha normalizada
     */
    public String normalizeNewPasswordOrThrow(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória");
        }

        String password = rawPassword.trim();
        if (password.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória");
        }

        return password;
    }

    /**
     * Resolve account ativa por slug.
     *
     * @param slug slug da conta
     * @return snapshot da conta
     */
    public PublicAccountView resolveReadyAccountBySlug(String slug) {
        return accountResolver.resolveActiveAccountBySlug(slug);
    }

    /**
     * Garante que a conta possua tenant schema utilizável.
     *
     * @param account snapshot da conta
     * @return tenant schema normalizado
     */
    public String requireTenantSchema(PublicAccountView account) {
        if (account == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada");
        }

        String tenantSchema = account.tenantSchema();
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_READY, "Conta sem schema");
        }

        return tenantSchema.trim();
    }

    /**
     * Garante que as claims mínimas do token de reset estejam presentes.
     *
     * @param tenantSchema schema tenant extraído do token
     * @param accountId id da conta extraído do token
     * @param email email extraído do token
     */
    public void assertResetTokenClaims(String tenantSchema, Long accountId, String email) {
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.INVALID_TOKEN, "Token inválido: tenantSchema ausente");
        }
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.INVALID_TOKEN, "Token inválido: accountId ausente");
        }
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.INVALID_TOKEN, "Token inválido: email ausente");
        }
    }
}