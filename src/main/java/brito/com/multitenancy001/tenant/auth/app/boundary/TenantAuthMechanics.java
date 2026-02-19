package brito.com.multitenancy001.tenant.auth.app.boundary;

import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;

/**
 * Boundary de mecânica de autenticação do Tenant.
 *
 * Responsabilidade:
 * - Operações de autenticação/refresco baseadas em infra (Spring Security/JWT)
 *   sem “vazar” detalhes para o app service.
 */
public interface TenantAuthMechanics {

    boolean verifyPasswordInTenant(AccountSnapshot account, String normalizedEmail, String rawPassword);

    JwtResult authenticateWithPassword(AccountSnapshot account, String normalizedEmail, String rawPassword);

    /**
     * Emite tokens para (account,email) sem pedir senha novamente.
     * Usado no CONFIRM (challenge já prova que senha foi validada no INIT).
     */
    JwtResult issueJwtForAccountAndEmail(AccountSnapshot account, String normalizedEmail);

    /**
     * Parse/validação do refresh token e extração de identidade mínima (SEM query).
     */
    TenantRefreshIdentity resolveRefreshIdentity(String refreshToken);

    /**
     * Refresh do JWT do Tenant:
     * - emite NOVO accessToken
     * - emite NOVO refreshToken (rotação)
     */
    JwtResult refreshTenantJwt(String refreshToken);
}
