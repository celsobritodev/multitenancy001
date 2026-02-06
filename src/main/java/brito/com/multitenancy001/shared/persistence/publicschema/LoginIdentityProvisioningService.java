package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginIdentityProvisioningService {

    private final JdbcTemplate jdbcTemplate;

    // =========================================================
    // TENANT
    // =========================================================

    public void ensureTenantIdentity(String email, Long accountId) {
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        jdbcTemplate.update("""
            INSERT INTO public.login_identities (email, user_type, account_id)
            VALUES (?, 'TENANT', ?)
            ON CONFLICT DO NOTHING
        """, emailNorm, accountId);
    }

    public void deleteTenantIdentity(String email, Long accountId) {
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        jdbcTemplate.update("""
            DELETE FROM public.login_identities
             WHERE email = ?
               AND user_type = 'TENANT'
               AND account_id = ?
        """, emailNorm, accountId);
    }

    // =========================================================
    // CONTROL PLANE
    // =========================================================

    /**
     * CP identities são "globais" (account_id NULL).
     */
    public void ensureControlPlaneIdentity(String email) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        jdbcTemplate.update("""
            INSERT INTO public.login_identities (email, user_type, account_id)
            VALUES (?, 'CONTROLPLANE', NULL)
            ON CONFLICT DO NOTHING
        """, emailNorm);
    }

    public void deleteControlPlaneIdentity(String email) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        jdbcTemplate.update("""
            DELETE FROM public.login_identities
             WHERE email = ?
               AND user_type = 'CONTROLPLANE'
               AND account_id IS NULL
        """, emailNorm);
    }

    /**
     * Troca de email no CP: remove a identity antiga e cria a nova.
     * (Executar dentro da mesma TX do update do usuário CP)
     */
    public void moveControlPlaneIdentity(String oldEmail, String newEmail) {
        String oldNorm = EmailNormalizer.normalizeOrNull(oldEmail);
        String newNorm = EmailNormalizer.normalizeOrNull(newEmail);

        if (oldNorm != null && !oldNorm.equals(newNorm)) {
            deleteControlPlaneIdentity(oldNorm);
        }
        if (newNorm != null) {
            ensureControlPlaneIdentity(newNorm);
        }
    }
}
