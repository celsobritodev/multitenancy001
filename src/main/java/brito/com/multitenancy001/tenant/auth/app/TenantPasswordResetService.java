package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TenantPasswordResetService {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantUserCommandService tenantUserCommandService;

    private final AccountResolver accountResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final TenantExecutor tenantExecutor;
    private final AppClock appClock;
    private final SecurityAuditService securityAuditService;

    public String generatePasswordResetToken(String slug, String email) {
        if (!StringUtils.hasText(slug)) throw new ApiException(ApiErrorCode.INVALID_SLUG, "Slug é obrigatório", 400);
        if (!StringUtils.hasText(email)) throw new ApiException(ApiErrorCode.INVALID_LOGIN, "Email é obrigatório", 400);

        String normalizedEmail = email.trim().toLowerCase();

        securityAuditService.record(
                SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                AuditOutcome.ATTEMPT,
                null,
                null,
                normalizedEmail,
                null,
                null,
                null,
                "{\"slug\":\"" + slug + "\"}"
        );

        AccountSnapshot account = accountResolver.resolveActiveAccountBySlug(slug);

        String tenantSchema = account.tenantSchema();
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_READY, "Conta sem schema", 409);
        }
        tenantSchema = tenantSchema.trim();

        String finalTenantSchema = tenantSchema;

        try {
            String token = tenantExecutor.runInTenantSchema(finalTenantSchema, () -> {
                TenantUser user = tenantUserQueryService.getUserByEmail(normalizedEmail, account.id());

                if (user.isDeleted() || user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
                    throw new ApiException(ApiErrorCode.USER_INACTIVE, "Usuário inativo", 403);
                }

                String passwordResetToken = jwtTokenProvider.generatePasswordResetToken(
                        user.getEmail(),
                        finalTenantSchema,
                        account.id()
                );

                user.setPasswordResetToken(passwordResetToken);
                user.setPasswordResetExpires(appClock.instant().plus(Duration.ofHours(1)));
                tenantUserCommandService.save(user);

                return passwordResetToken;
            });

            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                    AuditOutcome.SUCCESS,
                    null,
                    null,
                    normalizedEmail,
                    null,
                    account.id(),
                    finalTenantSchema,
                    "{\"expiresHours\":1}"
            );

            return token;

        } catch (Exception e) {
            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                    AuditOutcome.FAILURE,
                    null,
                    null,
                    normalizedEmail,
                    null,
                    account.id(),
                    finalTenantSchema,
                    "{\"reason\":\"error\"}"
            );
            throw e;
        }
    }

    public void resetPasswordWithToken(String token, String newPassword) {
        if (!StringUtils.hasText(token)) throw new ApiException(ApiErrorCode.INVALID_TOKEN, "Token inválido", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória", 400);

        String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(token);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String email = jwtTokenProvider.getEmailFromToken(token);

        securityAuditService.record(
                SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                AuditOutcome.ATTEMPT,
                null,
                null,
                email,
                null,
                accountId,
                tenantSchema,
                "{\"stage\":\"start\"}"
        );

        try {
            tenantExecutor.runInTenantSchema(tenantSchema, () -> {
                tenantUserCommandService.resetPasswordWithToken(accountId, email, token, newPassword);
                return null;
            });

            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    AuditOutcome.SUCCESS,
                    null,
                    null,
                    email,
                    null,
                    accountId,
                    tenantSchema,
                    "{\"stage\":\"done\"}"
            );
        } catch (Exception e) {
            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    AuditOutcome.FAILURE,
                    null,
                    null,
                    email,
                    null,
                    accountId,
                    tenantSchema,
                    "{\"reason\":\"error\"}"
            );
            throw e;
        }
    }
}
