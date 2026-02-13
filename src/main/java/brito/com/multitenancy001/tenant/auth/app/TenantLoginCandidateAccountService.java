package brito.com.multitenancy001.tenant.auth.app;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityResolver;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginCandidateAccount;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantLoginCandidateAccountService {

    private final LoginIdentityResolver loginIdentityResolver;

    public List<TenantLoginCandidateAccount> findCandidateAccounts(String normalizedEmail) {
        if (!StringUtils.hasText(normalizedEmail)) return List.of();

        return loginIdentityResolver.findTenantAccountsByEmail(normalizedEmail)
                .stream()
                .map(r -> new TenantLoginCandidateAccount(r.accountId(), r.displayName(), r.slug()))
                .toList();
    }
}
