package brito.com.multitenancy001.tenant.application.user;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import brito.com.multitenancy001.tenant.security.TenantRole;

@Service
public class TenantAdminCreationService {

    private final TenantUserRepository tenantUserRepository;

    public TenantAdminCreationService(TenantUserRepository tenantUserRepository) {
        this.tenantUserRepository = tenantUserRepository;
    }

    public TenantAdminResult createTenantOwner(Long accountId, String email, String name, String rawPassword) {
        validate(accountId, email, name, rawPassword);

        String normalizedEmail = email.trim().toLowerCase();

        TenantUser existing = tenantUserRepository
                .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, accountId)
                .orElse(null);

        if (existing != null) {
            // ✅ garante role (se já for owner ok; se não, promove)
            if (existing.getRole() != TenantRole.TENANT_OWNER) {
                existing.setRole(TenantRole.TENANT_OWNER);
                tenantUserRepository.save(existing); // onSave() garante permissões
            }
            return new TenantAdminResult(existing.getId(), existing.getEmail(), existing.getRole().name());
        }

        TenantUser owner = new TenantUser();
        owner.setAccountId(accountId);
        owner.setEmail(normalizedEmail);
        owner.setName(name);
        owner.setRole(TenantRole.TENANT_OWNER);

        // ⚠️ aqui você provavelmente quer um encoder (igual no ProvisioningFacade).
        // mantendo igual ao seu original:
        owner.setPassword(rawPassword);

        owner.setMustChangePassword(false);

        TenantUser saved = tenantUserRepository.save(owner); // onSave() garante permissões
        return new TenantAdminResult(saved.getId(), saved.getEmail(), saved.getRole().name());
    }

    private static void validate(Long accountId, String email, String name, String rawPassword) {
        if (accountId == null) {
            throw new ApiException("INVALID_ACCOUNT", "accountId é obrigatório", 400);
        }
        if (!StringUtils.hasText(email)) {
            throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (!looksLikeEmail(normalizedEmail)) {
            throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
        }
        if (!StringUtils.hasText(name)) {
            throw new ApiException("INVALID_NAME", "Nome é obrigatório", 400);
        }
        if (!StringUtils.hasText(rawPassword)) {
            throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);
        }
    }

    private static boolean looksLikeEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return false;
        if (at != email.lastIndexOf('@')) return false;
        return at < email.length() - 1;
    }

    public record TenantAdminResult(Long tenantAdminId, String email, String role) {}
}
