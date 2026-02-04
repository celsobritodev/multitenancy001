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

        // email é CITEXT em public.login_identities => comparação pode ser direta
        String sql = """
            select li.account_id,
                   a.display_name,
                   a.slug
              from public.login_identities li
              join public.accounts a on a.id = li.account_id
             where li.email = :email
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

