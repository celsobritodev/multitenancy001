package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginIdentityProvisioningService {

    private final JdbcTemplate jdbcTemplate;

    public void ensureTenantIdentity(String email, Long accountId) {
        if (accountId == null) return;

        String emailNorm = EmailNormalizer.normalizeOrNull(email);
        if (emailNorm == null) return;

        jdbcTemplate.update("""
            INSERT INTO public.login_identities (email, user_type, account_id)
            VALUES (?, 'TENANT', ?)
            ON CONFLICT DO NOTHING
        """, emailNorm, accountId);
    }
}

