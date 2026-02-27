package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * Provisionamento do "índice público" de login (public.login_identities).
 *
 * <p><b>Problema que este service resolve:</b></p>
 * <ul>
 *   <li>Em schema-per-tenant, você costuma ter TENANT via JPA (JpaTransactionManager) e PUBLIC via JDBC
 *       (DataSourceTransactionManager).</li>
 *   <li>Se você executar SQL PUBLIC "no meio" de uma TX TENANT/JPA no mesmo thread, o Spring pode estourar com:
 *       <pre>IllegalTransactionStateException: Pre-bound JDBC Connection found! JpaTransactionManager does not support running within DataSourceTransactionManager</pre>
 *   </li>
 * </ul>
 *
 * <p><b>Regra de ouro:</b> qualquer escrita PUBLIC deve rodar <b>após</b> a TX corrente finalizar (commit ou rollback)
 * quando chamada a partir de fluxo TENANT.</p>
 *
 * <p>Para isso, este service oferece:</p>
 * <ul>
 *   <li><b>Wrappers SAFE</b>: {@code ...AfterCompletion(...)} (use a partir do TENANT)</li>
 *   <li><b>Métodos NOW</b>: {@code ...Now(...)} (executa PUBLIC isolado via TransactionTemplate)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginIdentityProvisioningService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * TransactionManager do schema PUBLIC (obrigatório para executar SQL PUBLIC de forma isolada).
     *
     * <p>ATENÇÃO: este TM normalmente é um DataSourceTransactionManager (JDBC),
     * enquanto o tenant costuma ser JpaTransactionManager.</p>
     */
    @Qualifier("publicTransactionManager")
    private final PlatformTransactionManager publicTransactionManager;

    // =====================================================================
    // Compat (métodos antigos): mantenho para não quebrar callers existentes
    // =====================================================================

  
  

    // =====================================================================
    // SAFE wrappers (recomendado chamar SEMPRE estes a partir do TENANT)
    // =====================================================================

    /**
     * SAFE: agenda o provisioning do TENANT_ACCOUNT para rodar após a transação atual finalizar (commit ou rollback).
     *
     * <p>Use este método quando estiver no fluxo TENANT (JPA / TenantTx / etc.).</p>
     */
    public void ensureTenantIdentityAfterCompletion(String email, Long accountId) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (accountId == null || emailNorm == null) return;

        warnIfActiveTx("ensureTenantIdentityAfterCompletion", emailNorm, accountId);

        runAfterCompletion(() -> ensureTenantIdentityNow(emailNorm, accountId));
    }

    /**
     * SAFE: agenda o delete do TENANT_ACCOUNT para rodar após a transação atual finalizar (commit ou rollback).
     */
    public void deleteTenantIdentityAfterCompletion(String email, Long accountId) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (accountId == null || emailNorm == null) return;

        warnIfActiveTx("deleteTenantIdentityAfterCompletion", emailNorm, accountId);

        runAfterCompletion(() -> deleteTenantIdentityNow(emailNorm, accountId));
    }

    /**
     * SAFE: agenda o provisioning do CONTROLPLANE_USER para rodar após a transação atual finalizar (commit ou rollback).
     */
    public void ensureControlPlaneIdentityAfterCompletion(String email, Long controlPlaneUserId) {
        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (controlPlaneUserId == null || emailNorm == null) return;

        warnIfActiveTx("ensureControlPlaneIdentityAfterCompletion", emailNorm, null);

        runAfterCompletion(() -> ensureControlPlaneIdentityNow(emailNorm, controlPlaneUserId));
    }

    /**
     * SAFE: agenda o delete do CONTROLPLANE_USER (por userId) para rodar após a transação atual finalizar (commit ou rollback).
     */
    public void deleteControlPlaneIdentityByUserIdAfterCompletion(Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        warnIfActiveTx("deleteControlPlaneIdentityByUserIdAfterCompletion", null, null);

        runAfterCompletion(() -> deleteControlPlaneIdentityByUserIdNow(controlPlaneUserId));
    }

    /**
     * SAFE: agenda a troca de email do CONTROLPLANE_USER para rodar após a transação atual finalizar (commit ou rollback).
     */
    public void moveControlPlaneIdentityAfterCompletion(Long controlPlaneUserId, String newEmail) {
        String emailNorm = EmailNormalizer.normalizeOrNull(newEmail);
        if (controlPlaneUserId == null || emailNorm == null) return;

        warnIfActiveTx("moveControlPlaneIdentityAfterCompletion", emailNorm, null);

        runAfterCompletion(() -> moveControlPlaneIdentityNow(controlPlaneUserId, emailNorm));
    }

    // =====================================================================
    // NOW methods (somente PUBLIC, isolado; nunca chamar "no meio" do TENANT)
    // =====================================================================

    /**
     * Executa AGORA (PUBLIC) o UPSERT TENANT_ACCOUNT.
     *
     * <p>Somente chame diretamente se você tiver certeza que NÃO está dentro de uma TX TENANT/JPA no mesmo thread.
     * Caso contrário, use {@link #ensureTenantIdentityAfterCompletion(String, Long)}.</p>
     */
    public void ensureTenantIdentityNow(String emailNorm, Long accountId) {
        if (accountId == null) return;
        if (emailNorm == null) return;

        warnIfActiveTx("ensureTenantIdentityNow", emailNorm, accountId);

        runInPublicTransaction(() -> {
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

            if (log.isDebugEnabled()) {
                log.debug("✅ login_identity ensured (tenant) | email={} | accountId={} | rows={}",
                        emailNorm, accountId, rows);
            }
        });
    }

    /**
     * Executa AGORA (PUBLIC) o delete TENANT_ACCOUNT.
     */
    public void deleteTenantIdentityNow(String emailNorm, Long accountId) {
        if (accountId == null) return;
        if (emailNorm == null) return;

        warnIfActiveTx("deleteTenantIdentityNow", emailNorm, accountId);

        runInPublicTransaction(() -> {
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

            if (log.isDebugEnabled()) {
                log.debug("✅ login_identity deleted (tenant) | email={} | accountId={} | rows={}",
                        emailNorm, accountId, rows);
            }
        });
    }

    /**
     * Executa AGORA (PUBLIC) o UPSERT CONTROLPLANE_USER.
     */
    public void ensureControlPlaneIdentityNow(String emailNorm, Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;
        if (emailNorm == null) return;

        warnIfActiveTx("ensureControlPlaneIdentityNow", emailNorm, null);

        runInPublicTransaction(() -> {
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

            if (log.isDebugEnabled()) {
                log.debug("✅ login_identity ensured (controlplane) | email={} | userId={} | rows={}",
                        emailNorm, controlPlaneUserId, rows);
            }
        });
    }

    /**
     * Executa AGORA (PUBLIC) o delete CONTROLPLANE_USER por userId.
     */
    public void deleteControlPlaneIdentityByUserIdNow(Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        warnIfActiveTx("deleteControlPlaneIdentityByUserIdNow", null, null);

        runInPublicTransaction(() -> {
            String sql = """
                delete from public.login_identities
                 where subject_type = ?
                   and subject_id = ?
            """;

            int rows = jdbcTemplate.update(sql,
                    LoginIdentitySubjectType.CONTROLPLANE_USER.name(),
                    controlPlaneUserId
            );

            if (log.isDebugEnabled()) {
                log.debug("✅ login_identity deleted (controlplane) | userId={} | rows={}",
                        controlPlaneUserId, rows);
            }
        });
    }

    /**
     * Executa AGORA (PUBLIC) a troca de email do CONTROLPLANE_USER.
     *
     * <p>Implementação segura: remove o registro antigo (por userId) e recria com o novo email (upsert).</p>
     */
    public void moveControlPlaneIdentityNow(Long controlPlaneUserId, String newEmailNorm) {
        if (controlPlaneUserId == null) return;
        if (newEmailNorm == null) return;

        warnIfActiveTx("moveControlPlaneIdentityNow", newEmailNorm, null);

        runInPublicTransaction(() -> {
            deleteControlPlaneIdentityByUserIdNow(controlPlaneUserId);
            ensureControlPlaneIdentityNow(newEmailNorm, controlPlaneUserId);
        });
    }

    // =====================================================================
    // Scheduling / Isolation
    // =====================================================================

    /**
     * Garante que {@code op} rode após a transação corrente finalizar (commit OU rollback).
     *
     * <p>Se não existe synchronization ativa, executa imediatamente.</p>
     */
    private void runAfterCompletion(Runnable op) {
        if (op == null) return;

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            op.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    op.run();
                } catch (Exception e) {
                    log.warn("⚠️ Falha ao executar provisioning PUBLIC após completion | msg={}", e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Executa o runnable em uma transação PUBLIC isolada (TransactionTemplate).
     */
    private void runInPublicTransaction(Runnable op) {
        TransactionTemplate tt = new TransactionTemplate(publicTransactionManager);
        tt.executeWithoutResult(s -> op.run());
    }

    // =====================================================================
    // Diagnostics
    // =====================================================================

    private static void warnIfActiveTx(String op, String emailNorm, Long accountId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;

        Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();
        log.warn("⚠️ LoginIdentityProvisioningService chamado com TX ativa | op={} | email={} | accountId={} | resourcesKeys={}",
                op,
                emailNorm,
                accountId,
                summarizeResourceKeys(resources));
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