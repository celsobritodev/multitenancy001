package brito.com.multitenancy001.infrastructure.tenant;

import java.time.Instant;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.persistence.TransactionExecutor;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.TenantRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUserProvisioningFacade {

    private static final String REQUIRED_TABLE = "tenant_users";
    private static final String OWNER_NAME_FALLBACK = "Owner";

    private final TenantExecutor tenantExecutor;
    private final TransactionExecutor transactionExecutor;

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    private final AppClock appClock;

    public List<UserSummaryData> listUserSummaries(String schemaName, Long accountId, boolean onlyOperational) {
        tenantExecutor.assertReadyOrThrow(schemaName, REQUIRED_TABLE);

        return tenantExecutor.run(schemaName, () ->
                transactionExecutor.inTenantReadOnlyTx(() -> {

                    var users = onlyOperational
                            ? tenantUserRepository.findByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(accountId)
                            : tenantUserRepository.findByAccountId(accountId);

                    return users.stream()
                            .map(u -> new UserSummaryData(
                                    u.getId(),
                                    u.getAccountId(),
                                    u.getName(),
                                    u.getEmail(),
                                    u.getRole() == null ? null : TenantRoleName.valueOf(u.getRole().name()),
                                    u.isSuspendedByAccount(),
                                    u.isSuspendedByAdmin(),
                                    u.isDeleted()
                            ))
                            .toList();
                })
        );
    }

    /**
     * Cria o usuário dono (TENANT_OWNER) no schema do Tenant.
     *
     * ✅ Ajuste: permissões NÃO são inseridas manualmente.
     * - A entidade TenantUser já gerencia permissões via @ElementCollection (tenant_user_permissions)
     *   no @PrePersist/@PreUpdate (TenantUser.onSave()).
     */
    public UserSummaryData createTenantOwner(
            String schemaName,
            Long accountId,
            String ownerDisplayName,
            String email,
            String rawPassword
    ) {
        tenantExecutor.assertReadyOrThrow(schemaName, REQUIRED_TABLE);

        return tenantExecutor.run(schemaName, () ->
                transactionExecutor.inTenantTx(() -> {

                    if (accountId == null) {
                        throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
                    }
                    if (!StringUtils.hasText(email)) {
                        throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
                    }
                    if (!StringUtils.hasText(rawPassword)) {
                        throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);
                    }

                    String emailNorm = email.trim().toLowerCase();

                    boolean emailExists = tenantUserRepository.existsByEmailAndAccountId(emailNorm, accountId);
                    if (emailExists) {
                        throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já cadastrado nesta conta", 409);
                    }

                    String name = StringUtils.hasText(ownerDisplayName)
                            ? ownerDisplayName.trim()
                            : OWNER_NAME_FALLBACK;

                    TenantUser tenantUser = new TenantUser();
                    tenantUser.setAccountId(accountId);
                    tenantUser.setName(name);
                    tenantUser.setEmail(emailNorm);
                    tenantUser.setPassword(passwordEncoder.encode(rawPassword));
                    tenantUser.setRole(TenantRole.TENANT_OWNER);
                    tenantUser.setSuspendedByAccount(false);
                    tenantUser.setSuspendedByAdmin(false);
                    tenantUser.setTimezone("America/Sao_Paulo");
                    tenantUser.setLocale("pt_BR");

                    // ✅ aqui o onSave() da entidade garante as permissões do role.
                    TenantUser saved = tenantUserRepository.save(tenantUser);

                    return new UserSummaryData(
                            saved.getId(),
                            saved.getAccountId(),
                            saved.getName(),
                            saved.getEmail(),
                            saved.getRole() == null ? null : TenantRoleName.valueOf(saved.getRole().name()),
                            saved.isSuspendedByAccount(),
                            saved.isSuspendedByAdmin(),
                            saved.isDeleted()
                    );
                })
        );
    }

    public int suspendAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
                schemaName,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> tenantUserRepository.suspendAllByAccount(accountId)),
                0
        );
    }

    public int unsuspendAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
                schemaName,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> tenantUserRepository.unsuspendAllByAccount(accountId)),
                0
        );
    }

    public int softDeleteAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
                schemaName,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> tenantUserRepository.softDeleteAllByAccount(accountId, appClock.instant())),
                0
        );
    }

    public int restoreAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
                schemaName,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> tenantUserRepository.restoreAllByAccount(accountId)),
                0
        );
    }

    public void setSuspendedByAdmin(String schemaName, Long accountId, Long userId, boolean suspended) {
        tenantExecutor.assertReadyOrThrow(schemaName, REQUIRED_TABLE);

        tenantExecutor.run(schemaName, () ->
                transactionExecutor.inTenantTx(() -> {
                    int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
                    if (updated == 0) {
                        throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404);
                    }
                    return null;
                })
        );
    }

    public void setPasswordResetToken(String schemaName, Long accountId, Long userId, String token, Instant expiresAt) {
        tenantExecutor.runIfReady(schemaName, REQUIRED_TABLE, () ->
                transactionExecutor.inTenantTx(() -> {
                    TenantUser user = tenantUserRepository.findEnabledByIdAndAccountId(userId, accountId)
                            .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

                    user.setPasswordResetToken(token);
                    user.setPasswordResetExpires(expiresAt);
                    tenantUserRepository.save(user);
                    return null;
                })
        );
    }

    public TenantUser findByPasswordResetToken(String schemaName, Long accountId, String token) {
        tenantExecutor.assertReadyOrThrow(schemaName, REQUIRED_TABLE);

        return tenantExecutor.run(schemaName, () ->
                transactionExecutor.inTenantReadOnlyTx(() ->
                        tenantUserRepository.findByPasswordResetTokenAndAccountId(token, accountId)
                                .orElseThrow(() -> new ApiException("TOKEN_INVALID", "Token inválido", 400))
                )
        );
    }
}

