package brito.com.multitenancy001.tenant.auth.app.boundary;

import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;

/**
 * Boundary de mecânica de autenticação do Tenant.
 *
 * Responsabilidades:
 * - Definir contratos técnicos para login, refresh e logout no contexto Tenant.
 * - Isolar a camada de aplicação dos detalhes de JWT, Spring Security ou filtros.
 *
 * Papel arquitetural:
 * - Boundary (porta interna) entre Application Layer e Infrastructure Layer.
 * - Permite testar serviços de autenticação sem dependência de SecurityContext.
 *
 * Regras:
 * - NÃO contém lógica de persistência.
 * - NÃO depende de HTTP, filtros ou controllers.
 * - Implementações concretas vivem na camada infrastructure.
 *
 * Observação:
 * - Este contrato existe para manter o domínio e a aplicação desacoplados
 *   de frameworks de segurança.
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
