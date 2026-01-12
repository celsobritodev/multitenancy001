package brito.com.multitenancy001.infrastructure.tenant;



import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.exec.TenantExecutor;
import brito.com.multitenancy001.infrastructure.exec.TxExecutor;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.tenant.application.username.generator.UsernameGeneratorService;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import brito.com.multitenancy001.tenant.security.TenantRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUserAdminBridge {

    private static final String TENANT_USERS = "tenant_users";
	private final TenantExecutor tenantExecutor;
    private final TxExecutor txExecutor;

    private final TenantUserRepository tenantUserRepository;
    private final UsernameGeneratorService usernameGenerator;
    private final PasswordEncoder passwordEncoder;
    
    
    public List<UserSummaryData> listUserSummaries(String schemaName, Long accountId, boolean onlyActive) {
        tenantExecutor.assertReadyOrThrow(schemaName, TENANT_USERS);

        return tenantExecutor.run(schemaName, () ->
            txExecutor.tenantReadOnlyTx(() -> {

                var users = onlyActive
                        ? tenantUserRepository.findActiveUsersByAccount(accountId)
                        : tenantUserRepository.findByAccountId(accountId);

                return users.stream()
                        .map(u -> new UserSummaryData(
                                u.getId(),
                                u.getAccountId(),
                                u.getName(),
                                u.getUsername(),
                                u.getEmail(),
                                u.getRole() == null ? null : u.getRole().name(),
                                Boolean.TRUE.equals(u.isSuspendedByAccount()),
                                Boolean.TRUE.equals(u.isSuspendedByAdmin()),
                                u.isDeleted()
                        ))
                        .toList();
            })
        );
    }
 
    

    public TenantUser createTenantOwner(String schemaName, Long accountId, String email, String rawPassword) {
        tenantExecutor.assertReadyOrThrow(schemaName, TENANT_USERS);

        return tenantExecutor.run(schemaName, () ->
            txExecutor.tenantTx(() -> {

                boolean emailExists = tenantUserRepository.existsByEmailAndAccountId(email, accountId);
                if (emailExists) {
                    throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já cadastrado nesta conta", 409);
                }

                TenantUser u = new TenantUser();
                u.setAccountId(accountId);
                u.setName("Administrador");
                u.setEmail(email);
                u.setPassword(passwordEncoder.encode(rawPassword));
                u.setRole(TenantRole.TENANT_ACCOUNT_OWNER);
                u.setSuspendedByAccount(false);
                u.setSuspendedByAdmin(false);
                u.setCreatedAt(LocalDateTime.now());
                u.setTimezone("America/Sao_Paulo");
                u.setLocale("pt_BR");

                for (int attempt = 0; attempt < 5; attempt++) {
                    u.setUsername(usernameGenerator.generateFromEmail(email, accountId));
                    try {
                        return tenantUserRepository.save(u);
                    } catch (DataIntegrityViolationException e) {
                        log.warn("Colisão de username ao criar admin. Tentativa {}. accountId={} email={}",
                                attempt + 1, accountId, email);
                    }
                }

                throw new IllegalStateException("Failed to create tenant admin due to repeated username collisions");
            })
        );
    }

    public List<TenantUser> listUsers(String schemaName, Long accountId, boolean onlyActive) {
        tenantExecutor.assertReadyOrThrow(schemaName, TENANT_USERS);

        return tenantExecutor.run(schemaName, () ->
            txExecutor.tenantReadOnlyTx(() -> {
                return onlyActive
                    ? tenantUserRepository.findActiveUsersByAccount(accountId)
                    : tenantUserRepository.findByAccountId(accountId);
            })
        );
    }

    public void setSuspendedByAdmin(String schemaName, Long accountId, Long userId, boolean suspended) {
        tenantExecutor.assertReadyOrThrow(schemaName, TENANT_USERS);

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

    public int suspendAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
            schemaName, TENANT_USERS,
            () -> txExecutor.tenantRequiresNew(() -> tenantUserRepository.suspendAllByAccount(accountId)),
            0
        );
    }

    public int unsuspendAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
            schemaName, TENANT_USERS,
            () -> txExecutor.tenantRequiresNew(() -> tenantUserRepository.unsuspendAllByAccount(accountId)),
            0
        );
    }

    public int softDeleteAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
            schemaName, TENANT_USERS,
            () -> txExecutor.tenantRequiresNew(() -> {
                List<TenantUser> users = tenantUserRepository.findByAccountId(accountId);
                users.forEach(u -> { if (!u.isDeleted()) u.softDelete(); });
                tenantUserRepository.saveAll(users);
                return users.size();
            }),
            0
        );
    }

    public int restoreAllUsersByAccount(String schemaName, Long accountId) {
        return tenantExecutor.runIfReady(
            schemaName, TENANT_USERS,
            () -> txExecutor.tenantRequiresNew(() -> {
                List<TenantUser> users = tenantUserRepository.findByAccountId(accountId);
                users.forEach(u -> { if (u.isDeleted()) u.restore(); });
                tenantUserRepository.saveAll(users);
                return users.size();
            }),
            0
        );
    }
}
