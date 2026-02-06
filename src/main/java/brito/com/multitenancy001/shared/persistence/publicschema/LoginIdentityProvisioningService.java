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
    // TENANT (mantém seu modelo atual: email -> conta)
    // =========================================================

    public void ensureTenantIdentity(String email, Long accountId) {
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        jdbcTemplate.update("""
            INSERT INTO public.login_identities (email, subject_type, subject_id, account_id)
            VALUES (?, 'TENANT_ACCOUNT', ?, ?)
            ON CONFLICT DO NOTHING
        """, emailNorm, accountId, accountId);
    }

    // =========================================================
    // CONTROL PLANE (SaaS top: email -> subject_id(userId))
    // =========================================================

    public void ensureControlPlaneIdentity(String email, Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        // garante por subject (um CP user -> um identity)
        jdbcTemplate.update("""
            INSERT INTO public.login_identities (email, subject_type, subject_id, account_id)
            VALUES (?, 'CONTROLPLANE_USER', ?, NULL)
            ON CONFLICT (subject_type, subject_id)
            DO UPDATE SET email = EXCLUDED.email
        """, emailNorm, controlPlaneUserId);
    }

    public void deleteControlPlaneIdentityByUserId(Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        jdbcTemplate.update("""
            DELETE FROM public.login_identities
             WHERE subject_type = 'CONTROLPLANE_USER'
               AND subject_id = ?
        """, controlPlaneUserId);
    }

    /**
     * Troca de email no CP: atualiza a identity do subject (userId).
     * Executar dentro da mesma TX do update do usuário CP.
     */
    public void moveControlPlaneIdentity(Long controlPlaneUserId, String newEmail) {
        if (controlPlaneUserId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(newEmail);
        if (emailNorm == null) return;

        // Atualiza por subject. Se não existir, cria.
        int updated = jdbcTemplate.update("""
            UPDATE public.login_identities
               SET email = ?
             WHERE subject_type = 'CONTROLPLANE_USER'
               AND subject_id = ?
        """, emailNorm, controlPlaneUserId);

        if (updated == 0) {
            ensureControlPlaneIdentity(emailNorm, controlPlaneUserId);
        }
    }
}
