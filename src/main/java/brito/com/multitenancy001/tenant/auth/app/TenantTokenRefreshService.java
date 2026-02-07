package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEventAuditService;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TenantTokenRefreshService {

    private final JwtTokenProvider jwtTokenProvider;
    private final TenantExecutor tenantExecutor;
    private final TenantUserRepository tenantUserRepository;
    private final AppClock appClock;

    private final AuthEventAuditService authEventAuditService;

    public JwtResult refresh(String refreshToken) {

        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken é obrigatório", 400);
        }

        authEventAuditService.record(AuthDomain.TENANT, AuthEventType.TOKEN_REFRESH, AuditOutcome.ATTEMPT, null, null, null, null,
                "{\"stage\":\"start\"}");

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.TOKEN_REFRESH, AuditOutcome.FAILURE, null, null, null, null,
                    "{\"reason\":\"invalid_token\"}");
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        AuthDomain authDomain = jwtTokenProvider.getAuthDomainEnum(refreshToken);
        if (authDomain != AuthDomain.REFRESH) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.TOKEN_REFRESH, AuditOutcome.FAILURE, null, null, null, null,
                    "{\"reason\":\"invalid_auth_domain\"}");
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(refreshToken);
        if (!StringUtils.hasText(tenantSchema)) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.TOKEN_REFRESH, AuditOutcome.FAILURE, null, null, null, null,
                    "{\"reason\":\"missing_tenant_schema\"}");
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        String email = EmailNormalizer.normalizeOrNull(jwtTokenProvider.getEmailFromToken(refreshToken));
        if (!StringUtils.hasText(email)) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.TOKEN_REFRESH, AuditOutcome.FAILURE, null, null, null, tenantSchema,
                    "{\"reason\":\"missing_email\"}");
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);
        if (accountId == null) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.TOKEN_REFRESH, AuditOutcome.FAILURE, email, null, null, tenantSchema,
                    "{\"reason\":\"missing_account_id\"}");
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido (accountId ausente)", 401);
        }

        return tenantExecutor.run(tenantSchema, () -> {

            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(email, accountId)
                    .orElseThrow(() -> new ApiException("INVALID_REFRESH", "refreshToken inválido", 401));

            if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
                throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
            }

            tenantUserRepository.updateLastLogin(user.getId(), appClock.instant());

            var authorities = AuthoritiesFactory.forTenant(user);

            AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                    user,
                    tenantSchema,
                    appClock.instant(),
                    authorities
            );

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );

            String newAccessToken = jwtTokenProvider.generateTenantToken(authentication, accountId, tenantSchema);

            SystemRoleName role = TenantAuthSupport.toSystemRoleOrNull(user.getRole());

            JwtResult result = new JwtResult(
                    newAccessToken,
                    refreshToken,
                    user.getId(),
                    user.getEmail(),
                    role,
                    accountId,
                    tenantSchema
            );

            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.TOKEN_REFRESH, AuditOutcome.SUCCESS, result.email(), result.userId(), result.accountId(), result.tenantSchema(),
                    "{\"stage\":\"completed\"}");

            return result;
        });
    }
}
