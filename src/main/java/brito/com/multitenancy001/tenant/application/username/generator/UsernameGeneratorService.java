package brito.com.multitenancy001.tenant.application.username.generator;

import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.domain.username.UsernamePolicy;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsernameGeneratorService {

	private static final int SUFFIX_LEN = 8;

	private final UsernamePolicy policy;
	private final TenantUserRepository tenantUserRepository;

	/**
	 * Gera username baseado no email, garantindo: - normalização - limite máximo
	 * (sem cortar sufixo) - unicidade por accountId (best-effort + fallback)
	 */
	public String generateFromEmail(String email, Long accountId) {
    if (accountId == null) {
        throw new IllegalArgumentException("accountId is required");
    }
    if (email == null || !email.contains("@")) {
        throw new IllegalArgumentException("Invalid email");
    }

    String local = email.split("@", 2)[0].toLowerCase();
    String base = policy.normalizeBase(local);

    for (int attempt = 0; attempt < 10; attempt++) {
        String candidate = policy.build(base, randomSuffix(SUFFIX_LEN));
        if (!tenantUserRepository.existsByUsernameAndAccountId(candidate, accountId)) {
            return candidate;
        }
    }

    for (int counter = 2; counter < 200; counter++) {
        String candidate = policy.build(base, String.valueOf(counter));
        if (!tenantUserRepository.existsByUsernameAndAccountId(candidate, accountId)) {
            return candidate;
        }
    }

    throw new IllegalStateException("Could not generate a unique username after multiple attempts");
}

public String ensureUnique(String initialUsername, Long accountId) {
    if (accountId == null) {
        throw new IllegalArgumentException("accountId is required");
    }

    if (policy.isValid(initialUsername) &&
        !tenantUserRepository.existsByUsernameAndAccountId(initialUsername, accountId)) {
        return initialUsername;
    }

    String base = policy.extractBase(initialUsername);

    for (int attempt = 0; attempt < 10; attempt++) {
        String candidate = policy.build(base, randomSuffix(SUFFIX_LEN));
        if (!tenantUserRepository.existsByUsernameAndAccountId(candidate, accountId)) {
            return candidate;
        }
    }

    for (int counter = 2; counter < 200; counter++) {
        String candidate = policy.build(base, String.valueOf(counter));
        if (!tenantUserRepository.existsByUsernameAndAccountId(candidate, accountId)) {
            return candidate;
        }
    }

    throw new IllegalStateException("Could not ensure unique username after multiple attempts");
}


	
	private String randomSuffix(int len) {
		return UUID.randomUUID().toString().replace("-", "").substring(0, len);
	}
}
