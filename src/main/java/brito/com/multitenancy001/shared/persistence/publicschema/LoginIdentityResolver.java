package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolver de login identities no schema PUBLIC.
 *
 * <p><b>Responsabilidade:</b> executar consultas pequenas e determinísticas
 * para resolver identidades usadas no fluxo de login.</p>
 *
 * <p><b>Transação:</b>
 * as operações são read-only e rodam no PUBLIC; usamos {@code SUPPORTS} para:
 * <ul>
 *   <li>participar de TX existente (se houver)</li>
 *   <li>não “forçar” abertura de TX se o chamador não precisar</li>
 * </ul>
 * sempre com {@code transactionManager="publicTransactionManager"} para evitar ambiguidades.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginIdentityResolver {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(
            transactionManager = "publicTransactionManager",
            readOnly = true,
            propagation = Propagation.SUPPORTS
    )
    public List<LoginIdentityRow> findTenantAccountsByEmail(String email) {
        String normalized = EmailNormalizer.normalizeOrNull(email);
        if (normalized == null) return List.of();

        String sql = """
            select li.account_id,
                   a.display_name,
                   a.slug
              from public.login_identities li
              join public.accounts a on a.id = li.account_id
             where li.email = :email
               and li.subject_type = 'TENANT_ACCOUNT'
               and a.deleted = false
        """;

        var params = new MapSqlParameterSource("email", normalized);

        List<LoginIdentityRow> out = jdbcTemplate.query(sql, params, (rs, rowNum) -> new LoginIdentityRow(
                rs.getLong("account_id"),
                rs.getString("display_name"),
                rs.getString("slug")
        ));

        if (log.isDebugEnabled()) {
            log.debug("🔎 findTenantAccountsByEmail | email={} | rows={}", normalized, out.size());
        }

        return out;
    }

    /**
     * Resolve o Control Plane user por e-mail via login_identities.
     *
     * <p>Retorna {@code null} se não existir.</p>
     */
    @Transactional(
            transactionManager = "publicTransactionManager",
            readOnly = true,
            propagation = Propagation.SUPPORTS
    )
    public Long resolveControlPlaneUserIdByEmail(String email) {
        String normalized = EmailNormalizer.normalizeOrNull(email);
        if (normalized == null) return null;

        String sql = """
            select li.subject_id
              from public.login_identities li
             where li.email = :email
               and li.subject_type = 'CONTROLPLANE_USER'
             limit 1
        """;

        var params = new MapSqlParameterSource("email", normalized);

        List<Long> rows = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getLong("subject_id"));
        Long out = rows.isEmpty() ? null : rows.get(0);

        if (log.isDebugEnabled()) {
            log.debug("🔎 resolveControlPlaneUserIdByEmail | email={} | found={}", normalized, out != null);
        }

        return out;
    }

    @Transactional(
            transactionManager = "publicTransactionManager",
            readOnly = true,
            propagation = Propagation.SUPPORTS
    )
    public boolean existsControlPlaneIdentity(String email) {
        String normalized = EmailNormalizer.normalizeOrNull(email);
        if (normalized == null) return false;

        String sql = """
            select count(1)
              from public.login_identities li
             where li.email = :email
               and li.subject_type = 'CONTROLPLANE_USER'
        """;

        var params = new MapSqlParameterSource("email", normalized);

        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        boolean out = count != null && count > 0;

        if (log.isDebugEnabled()) {
            log.debug("🔎 existsControlPlaneIdentity | email={} | exists={}", normalized, out);
        }

        return out;
    }
}