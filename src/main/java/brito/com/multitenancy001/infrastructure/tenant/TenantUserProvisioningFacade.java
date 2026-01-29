package brito.com.multitenancy001.infrastructure.tenant;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.TxExecutor;
import brito.com.multitenancy001.shared.security.TenantRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUserProvisioningFacade {

    private static final String REQUIRED_TABLE = "tenant_users";
    private static final String OWNER_NAME_FALLBACK = "Owner";

    private final TenantExecutor tenantExecutor;
    private final TxExecutor txExecutor;

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    private final AppClock appClock;

    public List<UserSummaryData> listUserSummaries(String schemaName, Long accountId, boolean onlyOperational) {
        tenantExecutor.assertReadyOrThrow(schemaName, REQUIRED_TABLE);

        return tenantExecutor.run(schemaName, () ->
                txExecutor.tenantReadOnlyTx(() -> {

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
     * Retorna um snapshot (UserSummaryData) para evitar vazamento do domínio Tenant
     * para o contexto ControlPlane.
     *
     * Login é por EMAIL
     *
     * ✅ Ajuste: cria permissões em batch (1 roundtrip) na tabela tenant_user_permissions.
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
                txExecutor.tenantTx(() -> {

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

                    TenantUser saved = tenantUserRepository.save(tenantUser);

                    // ✅ Batch insert das permissões do TENANT_OWNER
                    Set<TenantPermission> permissions = TenantRolePermissions.permissionsFor(TenantRole.TENANT_OWNER);
                    batchInsertPermissions(saved.getId(), permissions);

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

    /**
     * Insere permissões em batch (evita N inserts via Hibernate).
     *
     * Assumimos que:
     * - TenantExecutor já bindou o schema na thread (search_path do multi-tenant provider).
     * - A tabela tenant_user_permissions existe no schema do tenant.
     */
   private void batchInsertPermissions(Long tenantUserId, Set<TenantPermission> permissions) {
    if (tenantUserId == null) return;
    if (permissions == null || permissions.isEmpty()) return;

    List<TenantPermission> list = new ArrayList<>(permissions);

    jdbcTemplate.batchUpdate(
        "INSERT INTO tenant_user_permissions (tenant_user_id, permission) " +
        "VALUES (?, ?) ON CONFLICT DO NOTHING",
        list,
        500,
        (PreparedStatement ps, TenantPermission perm) -> {
            ps.setLong(1, tenantUserId);
            ps.setString(2, perm.name());
        }
    );
}


    public int suspendAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
                schemaName,
                REQUIRED_TABLE,
                () -> txExecutor.tenantRequiresNew(() -> tenantUserRepository.suspendAllByAccount(accountId)),
                0
        );
    }

    public int unsuspendAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
                schemaName,
                REQUIRED_TABLE,
                () -> txExecutor.tenantRequiresNew(() -> tenantUserRepository.unsuspendAllByAccount(accountId)),
                0
        );
    }

    /**
     * Soft delete em massa de usuários no schema tenant.
     * Usado pelo ControlPlane ao excluir/cancelar conta.
     */
    public int softDeleteAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
                schemaName,
                REQUIRED_TABLE,
                () -> txExecutor.tenantRequiresNew(() -> tenantUserRepository.softDeleteAllByAccount(accountId, appClock.now())),
                0
        );
    }

    /**
     * Restore em massa de usuários no schema tenant.
     * Usado pelo ControlPlane ao restaurar conta.
     */
    public int restoreAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
                schemaName,
                REQUIRED_TABLE,
                () -> txExecutor.tenantRequiresNew(() -> tenantUserRepository.restoreAllByAccount(accountId)),
                0
        );
    }

    /**
     * Suspende/reativa um usuário por ADMIN (ação administrativa do ControlPlane).
     * identidade é EMAIL, mas aqui o alvo é por userId.
     */
    public void setSuspendedByAdmin(String schemaName, Long accountId, Long userId, boolean suspended) {
        tenantExecutor.assertReadyOrThrow(schemaName, REQUIRED_TABLE);

        tenantExecutor.run(schemaName, () ->
                txExecutor.tenantTx(() -> {
                    int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
                    if (updated == 0) {
                        throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404);
                    }
                    return null;
                })
        );
    }

    public void setPasswordResetToken(String schemaName, Long accountId, Long userId, String token, LocalDateTime expiresAt) {
        tenantExecutor.runIfReady(schemaName, REQUIRED_TABLE, () ->
                txExecutor.tenantTx(() -> {
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
                txExecutor.tenantReadOnlyTx(() ->
                        tenantUserRepository.findByPasswordResetTokenAndAccountId(token, accountId)
                                .orElseThrow(() -> new ApiException("TOKEN_INVALID", "Token inválido", 400))
                )
        );
    }
}
