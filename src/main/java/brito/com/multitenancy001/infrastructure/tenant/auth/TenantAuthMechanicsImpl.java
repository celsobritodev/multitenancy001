package brito.com.multitenancy001.infrastructure.tenant.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.security.TenantRoleMapper;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantAuthMechanicsImpl implements brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics {

    private static final String INVALID_CREDENTIALS_MSG = "usuario ou senha invalidos";

    private final TenantExecutor tenantExecutor;
    private final AuthenticationManager authenticationManager;
    private final TenantUserRepository tenantUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppClock appClock;

    @Override
    public boolean verifyPasswordInTenant(AccountSnapshot account, String normalizedEmail, String rawPassword) {
        if (account == null || account.id() == null) return false;

        String schemaName = account.schemaName();
        if (!StringUtils.hasText(schemaName)) return false;

        String tenantSchema = schemaName.trim();

        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(rawPassword)) return false;

        try {
            return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
                Authentication authRequest = new UsernamePasswordAuthenticationToken(normalizedEmail, rawPassword);
                authenticationManager.authenticate(authRequest);

                tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                        .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));

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
            throw new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
        }

        String schemaName = account.schemaName();
        if (!StringUtils.hasText(schemaName)) {
            throw new ApiException("ACCOUNT_NOT_READY", "Conta sem schema", 409);
        }

        String tenantSchema = schemaName.trim();

        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(rawPassword)) {
            throw new ApiException("INVALID_LOGIN", "email e senha são obrigatórios", 400);
        }

        try {
            return tenantExecutor.runInTenantSchema(tenantSchema, () -> {

                Authentication authRequest = new UsernamePasswordAuthenticationToken(normalizedEmail, rawPassword);
                authenticationManager.authenticate(authRequest);

                TenantUser user = tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                        .orElseThrow(() -> new UsernameNotFoundException("INVALID_USER"));

                ensureUserActive(user);

                tenantUserRepository.updateLastLogin(user.getId(), appClock.instant());

                var authorities = AuthoritiesFactory.forTenant(user);

                AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                        user,
                        tenantSchema,
                        appClock.instant(),
                        authorities
                );

                Authentication finalAuth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

                String accessToken = jwtTokenProvider.generateTenantToken(finalAuth, account.id(), tenantSchema);

                String refreshToken = jwtTokenProvider.generateRefreshToken(
                        user.getEmail(),
                        tenantSchema,
                        account.id()
                );

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
        } catch (BadCredentialsException e) {
            throw e;
        } catch (UsernameNotFoundException | InternalAuthenticationServiceException e) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("AUTH_ERROR", "Falha ao autenticar", 500);
        }
    }

    @Override
    public JwtResult issueJwtForAccountAndEmail(AccountSnapshot account, String normalizedEmail) {
        if (account == null || account.id() == null) {
            throw new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
        }

        String schemaName = account.schemaName();
        if (!StringUtils.hasText(schemaName)) {
            throw new ApiException("ACCOUNT_NOT_READY", "Conta sem schema", 409);
        }

        String tenantSchema = schemaName.trim();

        if (!StringUtils.hasText(normalizedEmail)) {
            throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        }

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {

            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(normalizedEmail, account.id())
                    .orElseThrow(() -> new ApiException("INVALID_LOGIN", "Usuário não encontrado", 401));

            ensureUserActive(user);

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

            String accessToken = jwtTokenProvider.generateTenantToken(authentication, account.id(), tenantSchema);

            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getEmail(),
                    tenantSchema,
                    account.id()
            );

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
    public JwtResult refreshTenantJwt(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken é obrigatório", 400);
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        AuthDomain authDomain = jwtTokenProvider.getAuthDomainEnum(refreshToken);
        if (authDomain != AuthDomain.REFRESH) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        final String tenantSchemaRaw = jwtTokenProvider.getTenantSchemaFromToken(refreshToken);
        if (!StringUtils.hasText(tenantSchemaRaw)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }
        final String tenantSchema = tenantSchemaRaw.trim();

        final String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        if (!StringUtils.hasText(email)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido (email ausente)", 401);
        }

        final Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);
        if (accountId == null) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido (accountId ausente)", 401);
        }

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {

            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(email, accountId)
                    .orElseThrow(() -> new ApiException("INVALID_REFRESH", "refreshToken inválido", 401));

            ensureUserActive(user);

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

            SystemRoleName role = TenantRoleMapper.toSystemRoleOrNull(user.getRole());

            return new JwtResult(
                    newAccessToken,
                    refreshToken,
                    user.getId(),
                    user.getEmail(),
                    role,
                    accountId,
                    tenantSchema
            );
        });
    }

    private static void ensureUserActive(TenantUser user) {
        if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
            throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
        }
    }
}
