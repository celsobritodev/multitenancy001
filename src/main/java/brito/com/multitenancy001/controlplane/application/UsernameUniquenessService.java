package brito.com.multitenancy001.controlplane.application;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.tenant.user.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsernameUniquenessService {

    private final TenantUserRepository tenantUserRepository;

    public String ensureUniqueUsername(
            String baseUsername,
            Long accountId
    ) {
        String username = baseUsername;
        int counter = 1;

        while (tenantUserRepository
                .existsByUsernameAndAccountId(username, accountId)) {
            username = baseUsername + counter;
            counter++;
        }

        return username;
    }
}

