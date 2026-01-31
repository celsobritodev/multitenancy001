package brito.com.multitenancy001.tenant.users.app;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.security.TenantRoleName;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantAdminCreationService {

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantAdminCreationService(
            TenantUserRepository tenantUserRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantUserRepository = tenantUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Cria (ou garante) o TENANT_OWNER dentro do tenant schema.
     *
     * Regras:
     * - EmailNormalizer é o único ponto de normalização.
     * - Se já existir, garante role TENANT_OWNER e retorna summary.
     * - Senha sempre é armazenada ENCODED (consistente com o restante do projeto).
     */
    public UserSummaryData createTenantOwner(Long accountId, String email, String name, String rawPassword) {
        validate(accountId, email, name, rawPassword);

        String normalizedEmail = EmailNormalizer.normalizeOrNull(email);
        if (normalizedEmail == null) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }

        TenantUser existing = tenantUserRepository
                .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, accountId)
                .orElse(null);

        if (existing != null) {
            // garante role OWNER se alguém criou com role diferente
            if (existing.getRole() != TenantRole.TENANT_OWNER) {
                existing.setRole(TenantRole.TENANT_OWNER);
                tenantUserRepository.save(existing);
            }

            return toSummary(existing);
        }

        TenantUser owner = TenantUser.builder()
                .accountId(accountId)
                .email(normalizedEmail)
                .name(name.trim())
                .password(passwordEncoder.encode(rawPassword))
                .role(TenantRole.TENANT_OWNER)
                .mustChangePassword(true)
                .build();

        TenantUser saved = tenantUserRepository.save(owner);
        return toSummary(saved);
    }

    private UserSummaryData toSummary(TenantUser u) {
        return new UserSummaryData(
                u.getId(),
                u.getAccountId(),
                u.getName(),
                u.getEmail(),
                u.getRole() == null ? null : TenantRoleName.valueOf(u.getRole().name()),
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                u.isDeleted()
        );
    }

    private void validate(Long accountId, String email, String name, String rawPassword) {
        if (accountId == null) {
            throw new ApiException("INVALID_ACCOUNT_ID", "accountId é obrigatório", 400);
        }
        if (!StringUtils.hasText(email)) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }
        if (!StringUtils.hasText(name)) {
            throw new ApiException("INVALID_NAME", "Nome é obrigatório", 400);
        }
        if (!StringUtils.hasText(rawPassword)) {
            throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);
        }
        if (rawPassword.length() < 8) {
            throw new ApiException("INVALID_PASSWORD", "Senha deve ter pelo menos 8 caracteres", 400);
        }
    }
}
