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

    // =========================================================
    // TENANT (email -> conta)
    // =========================================================

    /**
     * Garante a identity TENANT_ACCOUNT para o par (email, accountId).
     *
     * Uso típico:
     * - após criar o usuário tenant (ex.: tenant owner) na provisão de account/signup
     * - após trocar e-mail de um usuário tenant (se você suportar isso)
     */
    @Transactional
    public void ensureTenantIdentity(String email, Long accountId) {
        /* comentário: cria identity TENANT_ACCOUNT (idempotente) */
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        String sql =
                "INSERT INTO public.login_identities (email, subject_type, subject_id, account_id)\n" +
                "VALUES (?, 'TENANT_ACCOUNT', ?, ?)\n" +
                "ON CONFLICT (email, account_id) WHERE subject_type = 'TENANT_ACCOUNT'\n" +
                "DO NOTHING";

        jdbcTemplate.update(sql, emailNorm, accountId, accountId);
    }

    /**
     * Remove a identity TENANT_ACCOUNT de um e-mail específico em uma conta específica.
     * (Útil se você implementar remoção/soft-delete + limpeza de identity.)
     */
    @Transactional
    public void deleteTenantIdentity(String email, Long accountId) {
        /* comentário: remove identity TENANT_ACCOUNT de forma segura */
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        String sql =
                "DELETE FROM public.login_identities\n" +
                " WHERE subject_type = 'TENANT_ACCOUNT'\n" +
                "   AND email = ?\n" +
                "   AND account_id = ?";

        jdbcTemplate.update(sql, emailNorm, accountId);
    }

    // =========================================================
    // CONTROL PLANE (email -> subject_id(userId))
    // =========================================================

    /**
     * Garante a identity CONTROLPLANE_USER apontando para o userId do Control Plane.
     *
     * Regras:
     * - Um CP user (subject_id) sempre deve ter uma identity.
     * - Se o e-mail mudar, atualiza por subject.
     */
    @Transactional
    public void ensureControlPlaneIdentity(String email, Long controlPlaneUserId) {
        /* comentário: cria/atualiza identity CONTROLPLANE_USER (idempotente) */
        if (controlPlaneUserId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        String sql =
                "INSERT INTO public.login_identities (email, subject_type, subject_id, account_id)\n" +
                "VALUES (?, 'CONTROLPLANE_USER', ?, NULL)\n" +
                "ON CONFLICT (subject_type, subject_id) WHERE subject_type = 'CONTROLPLANE_USER'\n" +
                "DO UPDATE SET email = EXCLUDED.email";

        jdbcTemplate.update(sql, emailNorm, controlPlaneUserId);
    }

    /**
     * Remove identity CONTROLPLANE_USER pelo userId.
     */
    @Transactional
    public void deleteControlPlaneIdentityByUserId(Long controlPlaneUserId) {
        /* comentário: remove identity do CP por subject_id */
        if (controlPlaneUserId == null) return;

        String sql =
                "DELETE FROM public.login_identities\n" +
                " WHERE subject_type = 'CONTROLPLANE_USER'\n" +
                "   AND subject_id = ?";

        jdbcTemplate.update(sql, controlPlaneUserId);
    }

    /**
     * Troca de e-mail no Control Plane: atualiza a identity do subject (userId).
     * Executar dentro da mesma TX do update do usuário CP.
     */
    @Transactional
    public void moveControlPlaneIdentity(Long controlPlaneUserId, String newEmail) {
        /* comentário: atualiza email da identity do CP; cria se não existir */
        if (controlPlaneUserId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(newEmail);
        if (emailNorm == null) return;

        String updateSql =
                "UPDATE public.login_identities\n" +
                "   SET email = ?\n" +
                " WHERE subject_type = 'CONTROLPLANE_USER'\n" +
                "   AND subject_id = ?";

        int updated = jdbcTemplate.update(updateSql, emailNorm, controlPlaneUserId);

        if (updated == 0) {
            ensureControlPlaneIdentity(emailNorm, controlPlaneUserId);
        }
    }
}