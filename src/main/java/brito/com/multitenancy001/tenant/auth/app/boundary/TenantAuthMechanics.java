package brito.com.multitenancy001.tenant.auth.app.boundary;

import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;

public interface TenantAuthMechanics {

    boolean verifyPasswordInTenant(AccountSnapshot account, String normalizedEmail, String rawPassword);

    JwtResult authenticateWithPassword(AccountSnapshot account, String normalizedEmail, String rawPassword);

    /**
     * Emite tokens para (account,email) sem pedir senha novamente.
     * Usado no CONFIRM (challenge já prova que senha foi validada no INIT).
     */
    JwtResult issueJwtForAccountAndEmail(AccountSnapshot account, String normalizedEmail);

    JwtResult refreshTenantJwt(String refreshToken);
}
