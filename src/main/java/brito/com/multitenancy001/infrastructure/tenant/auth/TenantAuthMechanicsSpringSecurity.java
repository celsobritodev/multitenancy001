package brito.com.multitenancy001.infrastructure.tenant.auth;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.security.TenantRoleMapper;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class TenantAuthMechanicsSpringSecurity implements TenantAuthMechanics {

    private static final String INVALID_CREDENTIALS_MSG = "usuario ou senha invalidos";

    private final TenantExecutor tenantExecutor;
    private final AuthenticationManager authenticationManager;
    private final TenantUserRepository tenantUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppClock appClock;

    @Override
    public boolean verifyPasswordInTenant(AccountSnapshot account, String normalizedEmail, String rawPassword) {
        if (account == null || account.id() == null) return false;

        String tenantSchema = account.tenantSchema();
        if (!StringUtils.hasText(tenantSchema)) return false;

        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(rawPassword)) return false;

        try {
            return tenantExecutor.runInTenantSchema(tenantSchema.trim(), () -> {
                Authentication authRequest = new UsernamePasswordAuthenticationToken(normalizedEmail, rawPassword);
                authenticationManager.authenticate(authRequest);

                tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                        .orElseThrow(() -> new UsernameNotFoundException(ApiErrorCode.INVALID_USER.code()));

                return true;
            });
        } catch (BadCredentialsException | UsernameNotFoundException | InternalAuthenticationServiceException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public JwtResult authenticateWithPassword(AccountSnapshot account, String normalizedEmail, String rawPassword) {
        if (account == null || account.id() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada");
        }

        String tenantSchema = account.tenantSchema();
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_READY, "Conta não pronta");
        }
        tenantSchema = tenantSchema.trim();

        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(rawPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, INVALID_CREDENTIALS_MSG);
        }

        String finalTenantSchema = tenantSchema;

        try {
            return tenantExecutor.runInTenantSchema(finalTenantSchema, () -> {
                Authentication authRequest = new UsernamePasswordAuthenticationToken(normalizedEmail, rawPassword);
                authenticationManager.authenticate(authRequest);

                TenantUser user = tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_USER, INVALID_CREDENTIALS_MSG));

                user.setLastLoginAt(appClock.instant());
                tenantUserRepository.save(user);

                Set<GrantedAuthority> authorities = AuthoritiesFactory.forTenant(user);
                AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(user, finalTenantSchema, appClock.instant(), authorities);

                Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);

                String accessToken = jwtTokenProvider.generateTenantToken(auth, account.id(), finalTenantSchema);
                String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), finalTenantSchema, account.id());

                SystemRoleName roleName = TenantRoleMapper.toSystemRoleName(user.getRole());

                return new JwtResult(accessToken, refreshToken, user.getId(), user.getEmail(), roleName, account.id(), finalTenantSchema);
            });
        } catch (BadCredentialsException e) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, INVALID_CREDENTIALS_MSG);
        }
    }

    @Override
    public JwtResult issueJwtForAccountAndEmail(AccountSnapshot account, String normalizedEmail) {
        if (account == null || account.id() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada");
        }
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório");
        }

        String tenantSchema = account.tenantSchema();
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_READY, "Conta não pronta");
        }
        tenantSchema = tenantSchema.trim();

        String finalTenantSchema = tenantSchema;

        return tenantExecutor.runInTenantSchema(finalTenantSchema, () -> {
            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_USER, "Usuário não encontrado"));

            if (user.isDeleted() || user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
                throw new ApiException(ApiErrorCode.USER_INACTIVE, "Usuário inativo");
            }

            user.setLastLoginAt(appClock.instant());
            tenantUserRepository.save(user);

            Set<GrantedAuthority> authorities = AuthoritiesFactory.forTenant(user);
            AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(user, finalTenantSchema, appClock.instant(), authorities);

            Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);

            String accessToken = jwtTokenProvider.generateTenantToken(auth, account.id(), finalTenantSchema);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), finalTenantSchema, account.id());

            SystemRoleName roleName = TenantRoleMapper.toSystemRoleName(user.getRole());

            return new JwtResult(accessToken, refreshToken, user.getId(), user.getEmail(), roleName, account.id(), finalTenantSchema);
        });
    }

    @Override
    public JwtResult refreshTenantJwt(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório");
        }

        try {
            AuthDomain domain = jwtTokenProvider.getAuthDomainEnum(refreshToken);
            if (domain == null || domain != AuthDomain.REFRESH) {
                throw new ApiException(ApiErrorCode.INVALID_REFRESH, "Refresh token inválido");
            }

            String tenantSchema = jwtTokenProvider.getContextFromToken(refreshToken);
            Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);
            String email = jwtTokenProvider.getEmailFromToken(refreshToken);

            if (!StringUtils.hasText(tenantSchema) || accountId == null || !StringUtils.hasText(email)) {
                throw new ApiException(ApiErrorCode.INVALID_REFRESH, "Refresh token inválido");
            }

            String finalTenantSchema = tenantSchema.trim();

            return tenantExecutor.runInTenantSchema(finalTenantSchema, () -> {
                TenantUser user = tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(email, accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_USER, "Usuário não encontrado"));

                if (user.isDeleted() || user.isSuspendedByAccount() || user.isSuspendedByAdmin()) {
                    throw new ApiException(ApiErrorCode.USER_INACTIVE, "Usuário inativo");
                }

                Set<GrantedAuthority> authorities = AuthoritiesFactory.forTenant(user);
                AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(user, finalTenantSchema, appClock.instant(), authorities);
                Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);

                String accessToken = jwtTokenProvider.generateTenantToken(auth, accountId, finalTenantSchema);
                String newRefresh = jwtTokenProvider.generateRefreshToken(user.getEmail(), finalTenantSchema, accountId);

                SystemRoleName roleName = TenantRoleMapper.toSystemRoleName(user.getRole());

                return new JwtResult(accessToken, newRefresh, user.getId(), user.getEmail(), roleName, accountId, finalTenantSchema);
            });

        } catch (JwtException e) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "Refresh token inválido");
        }
    }
}
