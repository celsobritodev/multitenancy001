package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Provisionamento de Login Identities (schema PUBLIC).
 *
 * <p><b>Objetivo:</b></p>
 * <ul>
 *   <li>Garantir que fluxos de login (tenant e control-plane) consigam resolver "candidatos" por e-mail</li>
 *   <li>Evitar joins complexos/ambíguos em múltiplos schemas durante a autenticação</li>
 * </ul>
 *
 * <p><b>Importante (transação):</b></p>
 * <ul>
 *   <li>Este serviço <b>SEMPRE</b> opera no PUBLIC schema.</li>
 *   <li>Para evitar ambiguidades, usamos explicitamente {@code transactionManager="publicTransactionManager"}.</li>
 * </ul>
 *
 * <p><b>Diagnóstico:</b></p>
 * <ul>
 *   <li>Se for invocado com uma transação já ativa no thread, logamos warning com resources bindados.</li>
 *   <li>Isso ajuda a detectar cenários de “pre-bound JDBC connection” antes de abrir TX JPA.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginIdentityProvisioningService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(
            transactionManager = "publicTransactionManager",
            propagation = Propagation.REQUIRED
    )
    public void ensureTenantIdentity(String email, Long accountId) {
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        warnIfActiveTx("ensureTenantIdentity", emailNorm, accountId);

        String sql =
                "INSERT INTO public.login_identities (email, subject_type, subject_id, account_id)\n" +
                "VALUES (?, ?::text, ?, ?)\n" +
                "ON CONFLICT (email, account_id) WHERE subject_type = ?::text\n" +
                "DO NOTHING";

        int rows = jdbcTemplate.update(
                sql,
                emailNorm,
                LoginIdentitySubjectType.TENANT_ACCOUNT.name(),
                accountId,
                accountId,
                LoginIdentitySubjectType.TENANT_ACCOUNT.name()
        );

        if (log.isDebugEnabled()) {
            log.debug("🧩 login_identity ensured (tenant) | email={} | accountId={} | rows={}",
                    emailNorm, accountId, rows);
        }
    }

    @Transactional(
            transactionManager = "publicTransactionManager",
            propagation = Propagation.REQUIRED
    )
    public void deleteTenantIdentity(String email, Long accountId) {
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        warnIfActiveTx("deleteTenantIdentity", emailNorm, accountId);

        String sql =
                "DELETE FROM public.login_identities\n" +
                " WHERE subject_type = ?::text\n" +
                "   AND email = ?\n" +
                "   AND account_id = ?";

        int rows = jdbcTemplate.update(sql,
                LoginIdentitySubjectType.TENANT_ACCOUNT.name(),
                emailNorm,
                accountId
        );

        if (log.isDebugEnabled()) {
            log.debug("🧹 login_identity deleted (tenant) | email={} | accountId={} | rows={}",
                    emailNorm, accountId, rows);
        }
    }

    @Transactional(
            transactionManager = "publicTransactionManager",
            propagation = Propagation.REQUIRED
    )
    public void ensureControlPlaneIdentity(String email, Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        warnIfActiveTx("ensureControlPlaneIdentity", emailNorm, null);

        String sql =
                "INSERT INTO public.login_identities (email, subject_type, subject_id, account_id)\n" +
                "VALUES (?, ?::text, ?, NULL)\n" +
                "ON CONFLICT (subject_type, subject_id) WHERE subject_type = ?::text\n" +
                "DO UPDATE SET email = EXCLUDED.email";

        int rows = jdbcTemplate.update(sql,
                emailNorm,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name(),
                controlPlaneUserId,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name()
        );

        if (log.isDebugEnabled()) {
            log.debug("🧩 login_identity ensured (cp) | email={} | controlPlaneUserId={} | rows={}",
                    emailNorm, controlPlaneUserId, rows);
        }
    }

    @Transactional(
            transactionManager = "publicTransactionManager",
            propagation = Propagation.REQUIRED
    )
    public void deleteControlPlaneIdentityByUserId(Long controlPlaneUserId) {
        if (controlPlaneUserId == null) return;

        warnIfActiveTx("deleteControlPlaneIdentityByUserId", null, null);

        String sql =
                "DELETE FROM public.login_identities\n" +
                " WHERE subject_type = ?::text\n" +
                "   AND subject_id = ?";

        int rows = jdbcTemplate.update(sql,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name(),
                controlPlaneUserId
        );

        if (log.isDebugEnabled()) {
            log.debug("🧹 login_identity deleted (cp) | controlPlaneUserId={} | rows={}",
                    controlPlaneUserId, rows);
        }
    }

    @Transactional(
            transactionManager = "publicTransactionManager",
            propagation = Propagation.REQUIRED
    )
    public void moveControlPlaneIdentity(Long controlPlaneUserId, String newEmail) {
        if (controlPlaneUserId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(newEmail);
        if (emailNorm == null) return;

        warnIfActiveTx("moveControlPlaneIdentity", emailNorm, null);

        String updateSql =
                "UPDATE public.login_identities\n" +
                "   SET email = ?\n" +
                " WHERE subject_type = ?::text\n" +
                "   AND subject_id = ?";

        int updated = jdbcTemplate.update(updateSql,
                emailNorm,
                LoginIdentitySubjectType.CONTROLPLANE_USER.name(),
                controlPlaneUserId
        );

        if (updated == 0) {
            ensureControlPlaneIdentity(emailNorm, controlPlaneUserId);
        }

        if (log.isDebugEnabled()) {
            log.debug("🔁 login_identity moved (cp) | controlPlaneUserId={} | email={} | updated={}",
                    controlPlaneUserId, emailNorm, updated);
        }
    }

    private static void warnIfActiveTx(String op, String emailNorm, Long accountId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;

        Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();
        log.warn("⚠️ LoginIdentityProvisioningService chamado com TX já ativa | op={} | email={} | accountId={} | resourcesKeys={}",
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