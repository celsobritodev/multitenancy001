package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.infrastructure.tx.AfterTransactionCompletion;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Provisionamento do índice público de login (public.login_identities).
 *
 * <p><b>Objetivo:</b></p>
 * <ul>
 *   <li>Permitir que o login resolva rapidamente "quem é este email" (tenant vs control-plane),
 *       sem joins multi-schema.</li>
 * </ul>
 *
 * <p><b>Regra crítica (evitar "Pre-bound JDBC Connection found!"):</b></p>
 * <ul>
 *   <li>Nunca executar escrita PUBLIC no mesmo thread do commit/cleanup de um TX "chamador".</li>
 *   <li>Quando disparado de dentro de uma TX (sync ativa), agendamos AFTER COMPLETION
 *       e executamos em outro thread, abrindo {@code REQUIRES_NEW} no PUBLIC.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginIdentityProvisioningService {

    private final JdbcTemplate jdbcTemplate;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AfterTransactionCompletion afterTransactionCompletion;

    // =========================================================
    // TENANT_ACCOUNT (account_id != null)
    // =========================================================

    /**
     * SAFE: garante UPSERT da identidade TENANT_ACCOUNT após completion da TX atual.
     */
    public void ensureTenantIdentityAfterCompletion(String email, Long accountId) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (accountId == null || emailNorm == null) {
            log.debug("ensureTenantIdentityAfterCompletion ignorado: email={}, accountId={}", email, accountId);
            return;
        }

        log.info("📋 ensureTenantIdentityAfterCompletion CHAMADO | email={} | accountId={}", emailNorm, accountId);

        afterTransactionCompletion.runAfterCompletion(() -> {
            try {
                publicSchemaUnitOfWork.requiresNew(() -> {
                    ensureTenantIdentityNow(emailNorm, accountId);
                    return null;
                });
                log.info("✅ ensureTenantIdentityNow EXECUTADO (afterCompletion) | email={} | accountId={}", emailNorm, accountId);
            } catch (Exception e) {
                log.error("❌ Erro no ensureTenantIdentityAfterCompletion | email={} | accountId={} | msg={}",
                        emailNorm, accountId, e.getMessage(), e);
            }
        });
    }

    /**
     * SAFE: remove identidade TENANT_ACCOUNT após completion da TX atual.
     */
    public void deleteTenantIdentityAfterCompletion(String email, Long accountId) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (accountId == null || emailNorm == null) {
            log.debug("deleteTenantIdentityAfterCompletion ignorado: email={}, accountId={}", email, accountId);
            return;
        }

        log.info("📋 deleteTenantIdentityAfterCompletion CHAMADO | email={} | accountId={}", emailNorm, accountId);

        afterTransactionCompletion.runAfterCompletion(() -> {
            try {
                publicSchemaUnitOfWork.requiresNew(() -> {
                    deleteTenantIdentityNow(emailNorm, accountId);
                    return null;
                });
                log.info("✅ deleteTenantIdentityNow EXECUTADO (afterCompletion) | email={} | accountId={}", emailNorm, accountId);
            } catch (Exception e) {
                log.error("❌ Erro no deleteTenantIdentityAfterCompletion | email={} | accountId={} | msg={}",
                        emailNorm, accountId, e.getMessage(), e);
            }
        });
    }

    /**
     * NOW (somente PUBLIC isolado): UPSERT TENANT_ACCOUNT.
     * <p><b>Nunca</b> chamar no meio de uma TX TENANT no mesmo thread. Use o método SAFE acima.</p>
     */
    public void ensureTenantIdentityNow(String emailNorm, Long accountId) {
        if (accountId == null || emailNorm == null) return;

        warnIfActiveTx("ensureTenantIdentityNow", emailNorm, accountId);

        String sql = """
            insert into public.login_identities (email, subject_type, account_id, subject_id)
            values (?, ?, ?, ?)
            on conflict (email, account_id) where subject_type = 'TENANT_ACCOUNT'
            do update set
                email = excluded.email,
                subject_id = excluded.subject_id
        """;

        int rows = jdbcTemplate.update(
                sql,
                emailNorm,
                LoginIdentitySubjectType.TENANT_ACCOUNT.name(),
                accountId,
                accountId
        );

        log.debug("✅ login_identity ensured (tenant) | email={} | accountId={} | rows={}", emailNorm, accountId, rows);
    }

    /**
     * NOW (somente PUBLIC isolado): delete TENANT_ACCOUNT.
     */
    public void deleteTenantIdentityNow(String emailNorm, Long accountId) {
        if (accountId == null || emailNorm == null) return;

        warnIfActiveTx("deleteTenantIdentityNow", emailNorm, accountId);

        String sql = """
            delete from public.login_identities
             where subject_type = ?
               and email = ?
               and account_id = ?
        """;

        int rows = jdbcTemplate.update(sql,
                LoginIdentitySubjectType.TENANT_ACCOUNT.name(),
                emailNorm,
                accountId
        );

        log.debug("✅ login_identity deleted (tenant) | email={} | accountId={} | rows={}", emailNorm, accountId, rows);
    }

    /**
     * Diagnóstico: existe TENANT_ACCOUNT para (email, accountId)?
     */
    public boolean existsTenantIdentity(String email, Long accountId) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (accountId == null || emailNorm == null) return false;

        String sql = """
            select count(*)
              from public.login_identities
             where email = ?
               and account_id = ?
               and subject_type = 'TENANT_ACCOUNT'
        """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, emailNorm, accountId);
        boolean exists = count != null && count > 0;
        log.info("🔍 existsTenantIdentity | email={} | accountId={} | exists={}", emailNorm, accountId, exists);
        return exists;
    }

    // =========================================================
    // CONTROLPLANE_USER (account_id = null)
    // =========================================================

    /**
     * SAFE: garante UPSERT da identidade CONTROLPLANE_USER após completion da TX atual.
     *
     * <p>Regra:</p>
     * <ul>
     *   <li>{@code account_id} deve ser {@code null} para CONTROLPLANE_USER.</li>
     *   <li>{@code subject_id} é o id do ControlPlaneUser.</li>
     * </ul>
     */
    public void ensureControlPlaneIdentityAfterCompletion(String email, Long controlPlaneUserId) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (controlPlaneUserId == null || emailNorm == null) {
            log.debug("ensureControlPlaneIdentityAfterCompletion ignorado: email={}, userId={}", email, controlPlaneUserId);
            return;
        }

        log.info("📋 ensureControlPlaneIdentityAfterCompletion CHAMADO | email={} | userId={}", emailNorm, controlPlaneUserId);

        afterTransactionCompletion.runAfterCompletion(() -> {
            try {
                publicSchemaUnitOfWork.requiresNew(() -> {
                    ensureControlPlaneIdentityNow(emailNorm, controlPlaneUserId);
                    return null;
                });
                log.info("✅ ensureControlPlaneIdentityNow EXECUTADO (afterCompletion) | email={} | userId={}", emailNorm, controlPlaneUserId);
            } catch (Exception e) {
                log.error("❌ Erro no ensureControlPlaneIdentityAfterCompletion | email={} | userId={} | msg={}",
                        emailNorm, controlPlaneUserId, e.getMessage(), e);
            }
        });
    }

    /**
     * SAFE: remove identidade CONTROLPLANE_USER por userId após completion da TX atual.
     */
    public void deleteControlPlaneIdentityByUserIdAfterCompletion(Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        log.info("📋 deleteControlPlaneIdentityByUserIdAfterCompletion CHAMADO | userId={}", controlPlaneUserId);

        afterTransactionCompletion.runAfterCompletion(() -> {
            try {
                publicSchemaUnitOfWork.requiresNew(() -> {
                    deleteControlPlaneIdentityByUserIdNow(controlPlaneUserId);
                    return null;
                });
                log.info("✅ deleteControlPlaneIdentityByUserIdNow EXECUTADO (afterCompletion) | userId={}", controlPlaneUserId);
            } catch (Exception e) {
                log.error("❌ Erro no deleteControlPlaneIdentityByUserIdAfterCompletion | userId={} | msg={}",
                        controlPlaneUserId, e.getMessage(), e);
            }
        });
    }

    /**
     * SAFE: troca email da identidade CONTROLPLANE_USER por userId após completion da TX atual.
     */
    public void moveControlPlaneIdentityAfterCompletion(Long controlPlaneUserId, String newEmail) {
        String newEmailNorm = EmailNormalizer.normalizeOrNull(newEmail);
        if (controlPlaneUserId == null || newEmailNorm == null) {
            log.debug("moveControlPlaneIdentityAfterCompletion ignorado: userId={}, newEmail={}", controlPlaneUserId, newEmail);
            return;
        }

        log.info("📋 moveControlPlaneIdentityAfterCompletion CHAMADO | userId={} | newEmail={}", controlPlaneUserId, newEmailNorm);

        afterTransactionCompletion.runAfterCompletion(() -> {
            try {
                publicSchemaUnitOfWork.requiresNew(() -> {
                    moveControlPlaneIdentityNow(controlPlaneUserId, newEmailNorm);
                    return null;
                });
                log.info("✅ moveControlPlaneIdentityNow EXECUTADO (afterCompletion) | userId={} | newEmail={}", controlPlaneUserId, newEmailNorm);
            } catch (Exception e) {
                log.error("❌ Erro no moveControlPlaneIdentityAfterCompletion | userId={} | newEmail={} | msg={}",
                        controlPlaneUserId, newEmailNorm, e.getMessage(), e);
            }
        });
    }

    /**
     * NOW (somente PUBLIC isolado): UPSERT CONTROLPLANE_USER.
     */
    public void ensureControlPlaneIdentityNow(String emailNorm, Long controlPlaneUserId) {
        if (controlPlaneUserId == null || emailNorm == null) return;

        warnIfActiveTx("ensureControlPlaneIdentityNow", emailNorm, null);

        String sql = """
            insert into public.login_identities (email, subject_type, subject_id, account_id)
            values (?, ?, ?, null)
            on conflict (email) where subject_type = 'CONTROLPLANE_USER'
            do update set
                email = excluded.email,
                subject_id = excluded.subject_id
        """;

        int rows = jdbcTemplate.update(sql,
                emailNorm,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name(),
                controlPlaneUserId
        );

        log.debug("✅ login_identity ensured (controlplane) | email={} | userId={} | rows={}", emailNorm, controlPlaneUserId, rows);
    }

    /**
     * NOW (somente PUBLIC isolado): delete CONTROLPLANE_USER por userId.
     */
    public void deleteControlPlaneIdentityByUserIdNow(Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        warnIfActiveTx("deleteControlPlaneIdentityByUserIdNow", null, null);

        String sql = """
            delete from public.login_identities
             where subject_type = ?
               and subject_id = ?
               and account_id is null
        """;

        int rows = jdbcTemplate.update(sql,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name(),
                controlPlaneUserId
        );

        log.debug("✅ login_identity deleted (controlplane) | userId={} | rows={}", controlPlaneUserId, rows);
    }

    /**
     * NOW (somente PUBLIC isolado): troca email do CONTROLPLANE_USER por userId.
     */
    public void moveControlPlaneIdentityNow(Long controlPlaneUserId, String newEmailNorm) {
        if (controlPlaneUserId == null || newEmailNorm == null) return;

        warnIfActiveTx("moveControlPlaneIdentityNow", newEmailNorm, null);

        String sql = """
            update public.login_identities
               set email = ?
             where subject_type = ?
               and subject_id = ?
               and account_id is null
        """;

        int rows = jdbcTemplate.update(sql,
                newEmailNorm,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name(),
                controlPlaneUserId
        );

        log.debug("✅ login_identity moved (controlplane) | userId={} | rows={}", controlPlaneUserId, rows);
    }

    // =========================================================
    // Diagnostics
    // =========================================================

    private static void warnIfActiveTx(String op, String emailNorm, Long accountId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;

        Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();
        log.warn("⚠️ LoginIdentityProvisioningService chamado com TX ativa | op={} | email={} | accountId={} | resourcesKeys={}",
                op, emailNorm, accountId, summarizeResourceKeys(resources));
    }

    private static String summarizeResourceKeys(Map<Object, Object> resources) {
        if (resources == null || resources.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object k : resources.keySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(k == null ? "null" : k.getClass().getName());
        }
        sb.append("]");
        return sb.toString();
    }
}