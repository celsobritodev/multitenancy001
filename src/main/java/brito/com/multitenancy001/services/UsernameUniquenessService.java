package brito.com.multitenancy001.services;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.repositories.TenantUserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsernameUniquenessService {

    private final TenantUserRepository userTenantRepository;

    public String ensureUniqueUsername(
            String baseUsername,
            Long accountId
    ) {
        String username = baseUsername;
        int counter = 1;

        while (userTenantRepository
                .existsByUsernameAndAccountId(username, accountId)) {
            username = baseUsername + counter;
            counter++;
        }

        return username;
    }
}

