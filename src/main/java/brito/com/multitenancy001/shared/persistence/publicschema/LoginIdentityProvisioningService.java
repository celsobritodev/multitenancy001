// src/main/java/brito/com/multitenancy001/shared/persistence/publicschema/LoginIdentityProvisioningService.java
package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisionamento de Login Identities (schema PUBLIC).
 *
 * Objetivo:
 * - Garantir que /login (tenant e control-plane) consiga resolver "candidatos" por e-mail,
 *   sem depender de joins complexos em múltiplos schemas.
 *
 * Convenções do seu V9__create_table_login_identities.sql:
 * - TENANT_ACCOUNT: (email, account_id) é único (via índice parcial) e subject_id = account_id
 * - CONTROLPLANE_USER: (subject_type, subject_id) é único (via índice parcial) e account_id é NULL
 *
 * Observação importante:
 * - Para índices parciais, usamos o suporte do Postgres de:
 *     ON CONFLICT (cols) WHERE <predicate>
 *   para bater exatamente no índice parcial correspondente (evita conflito com o outro tipo).
 */
@Service
@RequiredArgsConstructor
public class LoginIdentityProvisioningService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void ensureTenantIdentity(String email, Long accountId) {
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        String sql =
                "INSERT INTO public.login_identities (email, subject_type, subject_id, account_id)\n" +
                "VALUES (?, ?::text, ?, ?)\n" +
                "ON CONFLICT (email, account_id) WHERE subject_type = ?::text\n" +
                "DO NOTHING";

        jdbcTemplate.update(sql, emailNorm,
                LoginIdentitySubjectType.TENANT_ACCOUNT.name(), accountId, accountId,
                LoginIdentitySubjectType.TENANT_ACCOUNT.name());
    }

    @Transactional
    public void deleteTenantIdentity(String email, Long accountId) {
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        String sql =
                "DELETE FROM public.login_identities\n" +
                " WHERE subject_type = ?::text\n" +
                "   AND email = ?\n" +
                "   AND account_id = ?";

        jdbcTemplate.update(sql, LoginIdentitySubjectType.TENANT_ACCOUNT.name(), emailNorm, accountId);
    }

    @Transactional
    public void ensureControlPlaneIdentity(String email, Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        String sql =
                "INSERT INTO public.login_identities (email, subject_type, subject_id, account_id)\n" +
                "VALUES (?, ?::text, ?, NULL)\n" +
                "ON CONFLICT (subject_type, subject_id) WHERE subject_type = ?::text\n" +
                "DO UPDATE SET email = EXCLUDED.email";

        jdbcTemplate.update(sql, emailNorm,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name(), controlPlaneUserId,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name());
    }

    @Transactional
    public void deleteControlPlaneIdentityByUserId(Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        String sql =
                "DELETE FROM public.login_identities\n" +
                " WHERE subject_type = ?::text\n" +
                "   AND subject_id = ?";

        jdbcTemplate.update(sql, LoginIdentitySubjectType.CONTROLPLANE_USER.name(), controlPlaneUserId);
    }

    @Transactional
    public void moveControlPlaneIdentity(Long controlPlaneUserId, String newEmail) {
        if (controlPlaneUserId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(newEmail);
        if (emailNorm == null) return;

        String updateSql =
                "UPDATE public.login_identities\n" +
                "   SET email = ?\n" +
                " WHERE subject_type = ?::text\n" +
                "   AND subject_id = ?";

        int updated = jdbcTemplate.update(updateSql, emailNorm,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name(), controlPlaneUserId);

        if (updated == 0) {
            ensureControlPlaneIdentity(emailNorm, controlPlaneUserId);
        }
    }
}