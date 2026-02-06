package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LoginIdentityResolver {

    private final NamedParameterJdbcTemplate jdbcTemplate;

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

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new LoginIdentityRow(
                rs.getLong("account_id"),
                rs.getString("display_name"),
                rs.getString("slug")
        ));
    }

    /**
     * "SaaS moderno top": resolve o CP user por subject_id (id do user),
     * e o resto do login carrega o user por ID.
     */
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
        return rows.isEmpty() ? null : rows.get(0);
    }

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
        return count != null && count > 0;
    }
}
