package brito.com.multitenancy001.infrastructure.publicschema;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LoginIdentityResolver {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** ✅ nome esperado pelo TenantAuthService */
    public List<LoginIdentityRow> findTenantAccountsByEmail(String email) {
        return findTenantsByEmail(email);
    }

    /** ✅ implementação real */
    public List<LoginIdentityRow> findTenantsByEmail(String email) {
        if (email == null || email.isBlank()) return List.of();

        String normalized = email.trim().toLowerCase();

        String sql = """
            select li.account_id,
                   a.display_name,
                   a.slug
              from public.login_identities li
              join public.accounts a on a.id = li.account_id
             where lower(li.email) = lower(:email)
             and li.user_type = 'TENANT'
               and a.deleted = false
        """;

        var params = new MapSqlParameterSource("email", normalized);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new LoginIdentityRow(
                rs.getLong("account_id"),
                rs.getString("display_name"),
                rs.getString("slug")
        ));
    }
}
