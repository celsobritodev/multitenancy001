package brito.com.multitenancy001.tenant.application.user;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.domain.user.permission.TenantUserPermission;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;

@Service
public class TenantAdminCreationService {

    private final TenantUserRepository tenantUserRepository;
    private final TenantUserPermissionBulkInserter permissionBulkInserter;

    public TenantAdminCreationService(
            TenantUserRepository tenantUserRepository,
            TenantUserPermissionBulkInserter permissionBulkInserter
    ) {
        this.tenantUserRepository = tenantUserRepository;
        this.permissionBulkInserter = permissionBulkInserter;
    }

    public TenantAdminResult createTenantOwner(Long accountId, String email, String name, String rawPassword) {
        validate(accountId, email, name, rawPassword);

        String normalizedEmail = email.trim().toLowerCase();

        TenantUser existing = tenantUserRepository
                .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, accountId)
                .orElse(null);

        if (existing != null) {
            // garante permissões do papel atual do usuário existente
            List<TenantUserPermission> perms = toUserPermissions(
                    TenantRolePermissions.permissionsFor(existing.getRole())
            );
            permissionBulkInserter.insertAll(existing.getId(), perms);

            return new TenantAdminResult(existing.getId(), existing.getEmail(), existing.getRole().name());
        }

        TenantUser admin = new TenantUser();
        admin.setAccountId(accountId);
        admin.setEmail(normalizedEmail);
        admin.setName(name);
        admin.setRole(TenantRole.TENANT_OWNER);

        // ajuste para teu encoder real (BCrypt, etc.)
        admin.setPassword(rawPassword);
        admin.setMustChangePassword(false);

        TenantUser saved = tenantUserRepository.save(admin);

        // ✅ Agora converte corretamente
        List<TenantUserPermission> perms = toUserPermissions(
                TenantRolePermissions.permissionsFor(TenantRole.TENANT_OWNER)
        );
        permissionBulkInserter.insertAll(saved.getId(), perms);

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

    /**
     * Converte o Set<TenantPermission> (enum) para List<TenantUserPermission> (record code),
     * compatível com seu bulk inserter / persistência.
     */
    private static List<TenantUserPermission> toUserPermissions(Set<TenantPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) return List.of();

        // code = enum.name() (ex.: TEN_USER_READ)
        return permissions.stream()
                .map(p -> new TenantUserPermission(p.name()))
                .toList();
    }

    public record TenantAdminResult(Long tenantAdminId, String email, String role) {}
}
