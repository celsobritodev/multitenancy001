package brito.com.multitenancy001.infrastructure.publicschema;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LoginIdentityProvisioningService {

    private final JdbcTemplate jdbcTemplate;

    public void ensureTenantIdentity(String email, Long accountId) {
        if (accountId == null) return;
        if (!StringUtils.hasText(email)) return;

        String emailNorm = email.trim().toLowerCase();

        jdbcTemplate.update("""
        	    INSERT INTO public.login_identities (email, user_type, account_id)
        	    VALUES (?, 'TENANT', ?)
        	    ON CONFLICT DO NOTHING
        	""", emailNorm, accountId);

    }
}
