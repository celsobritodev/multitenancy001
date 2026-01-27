package brito.com.multitenancy001.tenant.application.user.username;

import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.domain.username.UsernamePolicy;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsernameGeneratorService {

    private static final int SUFFIX_LEN = 8;

    private final UsernamePolicy usernamePolicy;
    private final TenantUserRepository tenantUserRepository;

    /**
     * Gera username baseado no email.
     *
     * Regra:
     * 1) tenta base "pura" (ex.: vendas)
     * 2) se colidir, tenta base + _ + counter (vendas_2, vendas_3...)
     * 3) se colidir demais, tenta base + _ + randomSuffix
     */
    public String generateFromEmail(String email, Long accountId) {
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        if (email == null || !email.contains("@")) throw new IllegalArgumentException("Invalid email");

        String local = email.split("@", 2)[0].toLowerCase();
        String base = usernamePolicy.asCandidate(local);

        // 1) tenta "vendas"
        if (!tenantUserRepository.existsByEmailAndAccountId(base, accountId)) {
            return base;
        }

        // 2) tenta "vendas_2", "vendas_3", ...
        for (int counter = 2; counter < 200; counter++) {
            String candidate = usernamePolicy.build(base, String.valueOf(counter));
            if (!tenantUserRepository.existsByEmailAndAccountId(candidate, accountId)) {
                return candidate;
            }
        }

        // 3) fallback aleatório (muito raro precisar)
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = usernamePolicy.build(base, randomSuffix(SUFFIX_LEN));
            if (!tenantUserRepository.existsByEmailAndAccountId(candidate, accountId)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Could not generate a unique username after multiple attempts");
    }

    /**
     * Garante unicidade dado um username inicial.
     * Útil se futuramente você permitir o usuário escolher username.
     */
    public String ensureUnique(String initialUsername, Long accountId) {
        if (accountId == null) throw new IllegalArgumentException("accountId is required");

        String candidate = usernamePolicy.asCandidate(initialUsername);

        if (usernamePolicy.isValid(candidate) && !tenantUserRepository.existsByEmailAndAccountId(candidate, accountId)) {
            return candidate;
        }

        String base = usernamePolicy.extractBase(candidate);

        for (int counter = 2; counter < 200; counter++) {
            String c = usernamePolicy.build(base, String.valueOf(counter));
            if (!tenantUserRepository.existsByEmailAndAccountId(c, accountId)) {
                return c;
            }
        }

        for (int attempt = 0; attempt < 20; attempt++) {
            String c = usernamePolicy.build(base, randomSuffix(SUFFIX_LEN));
            if (!tenantUserRepository.existsByEmailAndAccountId(c, accountId)) {
                return c;
            }
        }

        throw new IllegalStateException("Could not ensure unique username after multiple attempts");
    }

    private String randomSuffix(int len) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, len);
    }
}
