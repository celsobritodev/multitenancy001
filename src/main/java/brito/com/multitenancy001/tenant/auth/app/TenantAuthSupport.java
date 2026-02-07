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
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginResult;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class TenantAuthSupport {

    private static final String INVALID_CREDENTIALS_MSG = "usuario ou senha invalidos";

    private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    private final TenantUserRepository tenantUserRepository;
    private final TenantExecutor tenantExecutor;

    private final AppClock appClock;
    private final AuthEventAuditService authEventAuditService;

    static String normalizeEmailRequired(String raw) {
        String email = EmailNormalizer.normalizeOrNull(raw);
        if (!StringUtils.hasText(email)) {
            throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        }
        return email;
    }

    /**
     * Mantém o método só pra não espalhar lógica/casts:
     * aqui ele fica tipado e simples (e evita Object + toString()).
     */
    static SystemRoleName toSystemRoleOrNull(TenantRole tenantRole) {
        if (tenantRole == null) return null;

        return switch (tenantRole) {
            case TENANT_OWNER -> SystemRoleName.TENANT_OWNER;
            case TENANT_ADMIN -> SystemRoleName.TENANT_ADMIN;
            case TENANT_SUPPORT -> SystemRoleName.TENANT_SUPPORT;
            case TENANT_USER -> SystemRoleName.TENANT_USER;
            case TENANT_PRODUCT_MANAGER -> SystemRoleName.TENANT_PRODUCT_MANAGER;
            case TENANT_SALES_MANAGER -> SystemRoleName.TENANT_SALES_MANAGER;
            case TENANT_BILLING_MANAGER -> SystemRoleName.TENANT_BILLING_MANAGER;
            case TENANT_READ_ONLY -> SystemRoleName.TENANT_READ_ONLY;
            case TENANT_OPERATOR -> SystemRoleName.TENANT_OPERATOR;
        };
    }

    boolean verifyPasswordInTenant(AccountSnapshot account, String email, String password) {
        if (account == null || account.id() == null) return false;

        String tenantSchema = account.schemaName();
        if (!StringUtils.hasText(tenantSchema)) return false;

        try {
            return tenantExecutor.run(tenantSchema, () -> {
                Authentication authRequest = new UsernamePasswordAuthenticationToken(email, password);
                authenticationManager.authenticate(authRequest);

                tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(email, account.id())
                        .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

                return true;
            });
        } catch (BadCredentialsException | UsernameNotFoundException | InternalAuthenticationServiceException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    TenantLoginResult doTenantAuthentication(AccountSnapshot account, String email, String password) {
        if (account == null || account.id() == null) {
            throw new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
        }

        String tenantSchema = account.schemaName();
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException("ACCOUNT_NOT_READY", "Conta sem schema", 409);
        }

        try {
            return tenantExecutor.run(tenantSchema, () -> {

                Authentication authRequest = new UsernamePasswordAuthenticationToken(email, password);
                authenticationManager.authenticate(authRequest);

                TenantUser user = tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(email, account.id())
                        .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

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

                SystemRoleName role = toSystemRoleOrNull(user.getRole());

                JwtResult jwt = new JwtResult(
                        accessToken,
                        refreshToken,
                        user.getId(),
                        user.getEmail(),
                        role,
                        account.id(),
                        tenantSchema
                );

                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS, user.getEmail(), user.getId(), account.id(), tenantSchema,
                        "{\"mode\":\"password\"}");

                return new TenantLoginResult.LoginSuccess(jwt);
            });
        } catch (BadCredentialsException e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_FAILURE, AuditOutcome.FAILURE, email, null, account.id(), tenantSchema,
                    "{\"reason\":\"bad_credentials\"}");
            throw e;
        } catch (UsernameNotFoundException e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_FAILURE, AuditOutcome.FAILURE, email, null, account.id(), tenantSchema,
                    "{\"reason\":\"user_not_found\"}");
            throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
        } catch (InternalAuthenticationServiceException e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_FAILURE, AuditOutcome.FAILURE, email, null, account.id(), tenantSchema,
                    "{\"reason\":\"internal_auth\"}");
            throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
        } catch (Exception e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_FAILURE, AuditOutcome.FAILURE, email, null, account.id(), tenantSchema,
                    "{\"reason\":\"unexpected\"}");
            throw new ApiException("AUTH_ERROR", "Falha ao autenticar", 500);
        }
    }

    JwtResult issueJwtForAccountAndEmail(AccountSnapshot account, String email) {
        if (account == null || account.id() == null) {
            throw new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
        }

        String tenantSchema = account.schemaName();
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException("ACCOUNT_NOT_READY", "Conta sem schema", 409);
        }

        return tenantExecutor.run(tenantSchema, () -> {

            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(email, account.id())
                    .orElseThrow(() -> new ApiException("INVALID_LOGIN", "Usuário não encontrado", 401));

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

            String accessToken = jwtTokenProvider.generateTenantToken(authentication, account.id(), tenantSchema);

            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getEmail(),
                    tenantSchema,
                    account.id()
            );

            SystemRoleName role = toSystemRoleOrNull(user.getRole());

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
}
