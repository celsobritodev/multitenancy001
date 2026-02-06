package brito.com.multitenancy001.tenant.users.app;

import brito.com.multitenancy001.infrastructure.persistence.TransactionExecutor;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TenantUserService {

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;
    private final TransactionExecutor transactionExecutor;

    // =========================================================
    // LIMITS / COUNTS
    // =========================================================

    public long countUsersForLimit(Long accountId, UserLimitPolicy policy) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);

        return transactionExecutor.inTenantReadOnlyTx(() -> {
            if (policy == null) return tenantUserRepository.countByAccountIdAndDeletedFalse(accountId);

            return switch (policy) {
                // “seats in use” normalmente ignora suspensões, mas ignora deletados
                case SEATS_IN_USE -> tenantUserRepository.countByAccountIdAndDeletedFalse(accountId);

                // “enabled” = não deletado + não suspenso por admin/conta
                case SEATS_ENABLED -> tenantUserRepository.countEnabledUsersByAccount(accountId);

                default -> tenantUserRepository.countByAccountIdAndDeletedFalse(accountId);
            };
        });
    }

    public long countEnabledUsersByAccount(Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        return transactionExecutor.inTenantReadOnlyTx(() -> tenantUserRepository.countEnabledUsersByAccount(accountId));
    }

    // =========================================================
    // CREATE
    // =========================================================

    public TenantUser createTenantUser(
            Long accountId,
            String name,
            String email,
            String rawPassword,
            TenantRole role,
            String phone,
            String avatarUrl,
            String locale,
            String timezone,
            LinkedHashSet<String> permissionNames,
            Boolean mustChangePassword,
            EntityOrigin origin
    ) {
        return transactionExecutor.inTenantTx(() -> {

            if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
            if (!StringUtils.hasText(name)) throw new ApiException("INVALID_NAME", "Nome é obrigatório", 400);
            if (!StringUtils.hasText(email)) throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
            if (!StringUtils.hasText(rawPassword)) throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);
            if (role == null) throw new ApiException("INVALID_ROLE", "Role é obrigatória", 400);

            String normEmail = email.trim().toLowerCase();

            if (!normEmail.matches(ValidationPatterns.EMAIL_PATTERN)) {
                throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
            }
            if (!rawPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
                throw new ApiException("WEAK_PASSWORD", "Senha fraca", 400);
            }

            boolean exists = tenantUserRepository.existsByEmailAndAccountId(normEmail, accountId);
            if (exists) {
                throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já cadastrado nesta conta", 409);
            }

            TenantUser user = new TenantUser();
            user.setAccountId(accountId);
            user.setName(name.trim());
            user.setEmail(normEmail);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRole(role);

            user.setOrigin(origin == null ? EntityOrigin.ADMIN : origin);

            // ✅ NOVO: grava mustChangePassword (default false)
            user.setMustChangePassword(Boolean.TRUE.equals(mustChangePassword));
            Instant now = appClock.instant();

           // se NÃO exige troca, registra que a senha já foi “definida”
            if (!user.isMustChangePassword()) {
                user.setPasswordChangedAt(now);
            } else {
                user.setPasswordChangedAt(null);
            }
 

            user.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
            user.setAvatarUrl(StringUtils.hasText(avatarUrl) ? avatarUrl.trim() : null);

            // ✅ NOVO: persiste locale/timezone recebidos (trim + vazio -> null)
            user.setLocale(StringUtils.hasText(locale) ? locale.trim() : null);
            user.setTimezone(StringUtils.hasText(timezone) ? timezone.trim() : null);

            user.setSuspendedByAccount(false);
            user.setSuspendedByAdmin(false);

            // Permissões: base da role + extras desejadas
            Set<TenantPermission> base = new LinkedHashSet<>(TenantRolePermissions.permissionsFor(role));
            Set<TenantPermission> desired = new LinkedHashSet<>();

            if (permissionNames != null && !permissionNames.isEmpty()) {
                for (String p : permissionNames) {
                    if (!StringUtils.hasText(p)) continue;
                    try {
                        desired.add(TenantPermission.valueOf(p.trim()));
                    } catch (IllegalArgumentException ex) {
                        throw new ApiException("INVALID_PERMISSION", "Permissão inválida: " + p, 400);
                    }
                }
            }

            desired = PermissionScopeValidator.validateTenantPermissionsStrict(desired);

            Set<TenantPermission> finalPerms = new LinkedHashSet<>(base);
            finalPerms.addAll(desired);

            user.setPermissions(finalPerms);

            // ✅ mantém comportamento atual: fallback só se continuar vazio/nulo
            if (!StringUtils.hasText(user.getLocale())) user.setLocale("pt_BR");
            if (!StringUtils.hasText(user.getTimezone())) user.setTimezone("America/Sao_Paulo");

            return tenantUserRepository.save(user);
        });
    }

    // =========================================================
    // READ / LIST
    // =========================================================

    public TenantUser getUser(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                        .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404))
        );
    }

    public TenantUser getEnabledUser(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findEnabledByIdAndAccountId(userId, accountId)
                        .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário habilitado não encontrado", 404))
        );
    }

    public TenantUser getUserByEmail(String email, Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (!StringUtils.hasText(email)) throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);

        String normEmail = email.trim().toLowerCase();

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findByEmailAndAccountIdAndDeletedFalse(normEmail, accountId)
                        .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404))
        );
    }

    public List<TenantUser> listUsers(Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findByAccountIdAndDeletedFalse(accountId)
        );
    }

    public List<TenantUser> listEnabledUsers(Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findEnabledUsersByAccount(accountId)
        );
    }

    // =========================================================
    // UPDATE: STATUS / PROFILE / PASSWORD
    // =========================================================

    public void setSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);

        transactionExecutor.inTenantTx(() -> {
            int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
            if (updated == 0) throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404);
        });
    }

    public void setSuspendedByAccount(Long accountId, Long userId, boolean suspended) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);

        transactionExecutor.inTenantTx(() -> {
            int updated = tenantUserRepository.setSuspendedByAccount(accountId, userId, suspended);
            if (updated == 0) throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404);
        });
    }

    public TenantUser updateProfile(
            Long userId,
            Long accountId,
            String name,
            String phone,
            String avatarUrl,
            String locale,
            String timezone,
            Instant now
    ) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);

        return transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (StringUtils.hasText(name)) user.setName(name.trim());
            if (StringUtils.hasText(phone)) user.setPhone(phone.trim());
            if (StringUtils.hasText(locale)) user.setLocale(locale.trim());
            if (StringUtils.hasText(timezone)) user.setTimezone(timezone.trim());

            // ✅ avatarUrl: suporta atualizar OU limpar
            // - null  -> não altera
            // - ""    -> limpa (salva null)
            // - "xxx" -> salva trim()
            if (avatarUrl != null) {
                String trimmed = avatarUrl.trim();
                user.setAvatarUrl(trimmed.isEmpty() ? null : trimmed);
            }

            return tenantUserRepository.save(user);
        });
    }

    public TenantUser resetPassword(Long userId, Long accountId, String newPassword) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);

        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("WEAK_PASSWORD", "Senha fraca", 400);
        }

        return transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            Instant now = appClock.instant();

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
            user.setPasswordChangedAt(now);
            user.setPasswordResetToken(null);
            user.setPasswordResetExpires(null);

            return tenantUserRepository.save(user);
        });
    }

    public void resetPasswordWithToken(Long accountId, String email, String token, String newPassword) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (!StringUtils.hasText(token)) throw new ApiException("TOKEN_REQUIRED", "token é obrigatório", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);

        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("WEAK_PASSWORD", "Senha fraca", 400);
        }

        transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findByPasswordResetTokenAndAccountId(token, accountId)
                    .orElseThrow(() -> new ApiException("TOKEN_INVALID", "Token inválido", 400));

            Instant now = appClock.instant();

            if (user.getPasswordResetExpires() == null || user.getPasswordResetExpires().isBefore(now)) {
                throw new ApiException("TOKEN_EXPIRED", "Token expirado", 400);
            }

            // Compat/segurança: se veio email no token, confere
            if (StringUtils.hasText(email) && user.getEmail() != null) {
                String tokenLogin = email.trim().toLowerCase();
                if (!user.getEmail().trim().equalsIgnoreCase(tokenLogin)) {
                    throw new ApiException("TOKEN_INVALID", "Token inválido", 400);
                }
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
            user.setPasswordChangedAt(now);

            user.setPasswordResetToken(null);
            user.setPasswordResetExpires(null);

            tenantUserRepository.save(user);
        });
    }

    // =========================================================
    // DELETE / RESTORE
    // =========================================================

    public void softDelete(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);

        transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isDeleted()) return;

            Instant now = appClock.instant();
            user.softDelete(now, appClock.epochMillis());
            tenantUserRepository.save(user);
        });
    }

    public TenantUser restore(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);

        return transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            user.restore();
            return tenantUserRepository.save(user);
        });
    }

    public void hardDelete(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);

        transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            tenantUserRepository.delete(user);
        });
    }

    public TenantUser save(TenantUser user) {
        if (user == null) throw new ApiException("INVALID_REQUEST", "Usuário inválido", 400);
        return transactionExecutor.inTenantTx(() -> tenantUserRepository.save(user));
    }

    // =========================================================
    // ROLE TRANSFER (OWNER)
    // =========================================================

    public void transferTenantOwnerRole(Long accountId, Long fromUserId, Long toUserId) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (fromUserId == null) throw new ApiException("FROM_USER_REQUIRED", "fromUserId é obrigatório", 400);
        if (toUserId == null) throw new ApiException("TO_USER_REQUIRED", "toUserId é obrigatório", 400);
        if (fromUserId.equals(toUserId)) {
            throw new ApiException("INVALID_TRANSFER", "Não é possível transferir para si mesmo", 400);
        }

        transactionExecutor.inTenantTx(() -> {
            TenantUser from = tenantUserRepository.findEnabledByIdAndAccountId(fromUserId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário origem não encontrado/habilitado", 404));

            if (from.getRole() == null || !from.getRole().isTenantOwner()) {
                throw new ApiException("FORBIDDEN", "Apenas o TENANT_OWNER pode transferir", 403);
            }

            TenantUser to = tenantUserRepository.findEnabledByIdAndAccountId(toUserId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário destino não encontrado/habilitado", 404));

            // troca roles
            from.setRole(TenantRole.TENANT_ADMIN);
            to.setRole(TenantRole.TENANT_OWNER);

            // zera permissões custom e volta base do papel (evita “herdar” extras indevidos)
            from.setPermissions(new LinkedHashSet<>(TenantRolePermissions.permissionsFor(from.getRole())));
            to.setPermissions(new LinkedHashSet<>(TenantRolePermissions.permissionsFor(to.getRole())));

            tenantUserRepository.save(from);
            tenantUserRepository.save(to);
        });
    }
    
    public void changeMyPassword(
            Long userId,
            Long accountId,
            String currentPassword,
            String newPassword,
            String confirmNewPassword
    ) {
        if (accountId == null) throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException("USER_REQUIRED", "userId é obrigatório", 400);

        if (!StringUtils.hasText(currentPassword)) {
            throw new ApiException("CURRENT_PASSWORD_REQUIRED", "Senha atual é obrigatória", 400);
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new ApiException("NEW_PASSWORD_REQUIRED", "Nova senha é obrigatória", 400);
        }
        if (!StringUtils.hasText(confirmNewPassword)) {
            throw new ApiException("CONFIRM_PASSWORD_REQUIRED", "Confirmar nova senha é obrigatório", 400);
        }

        if (!newPassword.equals(confirmNewPassword)) {
            throw new ApiException("PASSWORD_MISMATCH", "Nova senha e confirmação não conferem", 400);
        }

        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException("WEAK_PASSWORD", "Senha fraca", 400);
        }

        transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            // ✅ valida senha atual
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new ApiException("CURRENT_PASSWORD_INVALID", "Senha atual inválida", 400);
            }

            Instant now = appClock.instant();

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
            user.setPasswordChangedAt(now);

            // ✅ limpeza compatível com teu fluxo atual
            user.setPasswordResetToken(null);
            user.setPasswordResetExpires(null);

            tenantUserRepository.save(user);
            return null;
        });
    }

    
}

