package brito.com.multitenancy001.infrastructure.tenant.auth;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantContextExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.PublicAccountView;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantRefreshIdentity;
import brito.com.multitenancy001.tenant.security.TenantRoleMapper;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Implementação de {@link TenantAuthMechanics} usando repository + password encoder + JWT.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar senha no schema do tenant.</li>
 *   <li>Autenticar tenant e emitir access/refresh token.</li>
 *   <li>Emitir JWT sem senha no fluxo CONFIRM.</li>
 *   <li>Resolver identidade mínima do refresh token sem query.</li>
 *   <li>Realizar refresh com rotação de refresh token.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenantAuthMechanicsSpringSecurity implements TenantAuthMechanics {

    private static final String INVALID_CREDENTIALS_MSG = "usuario ou senha invalidos";

    private final TenantContextExecutor tenantExecutor;
    private final TenantUserRepository tenantUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    @Override
    public boolean verifyPasswordInTenant(PublicAccountView account, String normalizedEmail, String rawPassword) {
        if (account == null || account.id() == null) return false;
        if (!StringUtils.hasText(account.tenantSchema())) return false;
        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(rawPassword)) return false;

        final String tenantSchema = account.tenantSchema().trim();

        try {
            return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
                TenantUser user = tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                        .orElse(null);

                if (user == null) return false;
                if (!isActive(user)) return false;

                String encoded = user.getPassword();
                return StringUtils.hasText(encoded) && passwordEncoder.matches(rawPassword, encoded);
            });
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public JwtResult authenticateWithPassword(PublicAccountView account, String normalizedEmail, String rawPassword) {
        if (!verifyPasswordInTenant(account, normalizedEmail, rawPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MSG, 401);
        }
        return issueJwtForAccountAndEmail(account, normalizedEmail);
    }

    @Override
    public JwtResult issueJwtForAccountAndEmail(PublicAccountView account, String normalizedEmail) {
        if (account == null || account.id() == null || !StringUtils.hasText(account.tenantSchema())) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Conta/tenant inválido para autenticação", 400);
        }
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório", 400);
        }

        final String tenantSchema = account.tenantSchema().trim();

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MSG, 401));

            ensureUserActive(user);

            tenantUserRepository.updateLastLogin(user.getId(), appClock.instant());

            var authorities = AuthoritiesFactory.forTenant(user);

            AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                    user,
                    tenantSchema,
                    appClock.instant(),
                    authorities
            );

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);

            String accessToken = jwtTokenProvider.generateTenantToken(authentication, account.id(), tenantSchema);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), tenantSchema, account.id());

            SystemRoleName role = TenantRoleMapper.toSystemRoleOrNull(user.getRole());

            return new JwtResult(
                    accessToken,
                    refreshToken,
                    user.getId(),
                    user.getEmail(),
                    role,
                    account.id(),
                    tenantSchema
            );
        });
    }

    @Override
    public TenantRefreshIdentity resolveRefreshIdentity(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        AuthDomain authDomain = jwtTokenProvider.getAuthDomainEnum(refreshToken);
        if (authDomain != AuthDomain.REFRESH) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        String tenantSchemaRaw = jwtTokenProvider.getTenantSchemaFromToken(refreshToken);
        if (!StringUtils.hasText(tenantSchemaRaw)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
        }

        String tenantSchema = tenantSchemaRaw.trim();

        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (email ausente)", 401);
        }

        Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido (accountId ausente)", 401);
        }

        return new TenantRefreshIdentity(email.trim(), accountId, tenantSchema);
    }

    @Override
    public JwtResult refreshTenantJwt(String refreshToken) {
        TenantRefreshIdentity id = resolveRefreshIdentity(refreshToken);

        return tenantExecutor.runInTenantSchema(id.tenantSchema(), () -> {

            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(id.email(), id.accountId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401));

            ensureUserActive(user);

            tenantUserRepository.updateLastLogin(user.getId(), appClock.instant());

            var authorities = AuthoritiesFactory.forTenant(user);

            AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                    user,
                    id.tenantSchema(),
                    appClock.instant(),
                    authorities
            );

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );

            String newAccessToken = jwtTokenProvider.generateTenantToken(authentication, id.accountId(), id.tenantSchema());
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), id.tenantSchema(), id.accountId());

            SystemRoleName role = TenantRoleMapper.toSystemRoleOrNull(user.getRole());

            return new JwtResult(
                    newAccessToken,
                    newRefreshToken,
                    user.getId(),
                    user.getEmail(),
                    role,
                    id.accountId(),
                    id.tenantSchema()
            );
        });
    }

    private static void ensureUserActive(TenantUser user) {
        if (!isActive(user)) {
            throw new ApiException(ApiErrorCode.USER_INACTIVE, "Usuário inativo", 403);
        }
    }

    private static boolean isActive(TenantUser user) {
        return !user.isDeleted()
                && !user.isSuspendedByAccount()
                && !user.isSuspendedByAdmin();
    }
}