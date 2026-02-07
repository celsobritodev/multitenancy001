package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEventAuditService;
import brito.com.multitenancy001.infrastructure.publicschema.auth.TenantLoginChallenge;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityRow;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginConfirmCommand;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginInitCommand;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginResult;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantSelectionOptionData;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantAuthService {

    private static final String INVALID_CREDENTIALS_MSG = "usuario ou senha invalidos";

    private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    private final AccountResolver accountResolver;
    private final LoginIdentityResolver loginIdentityResolver;

    private final TenantUserRepository tenantUserRepository;
    private final PublicExecutor publicExecutor;
    private final TenantExecutor tenantExecutor;

    private final TenantLoginChallengeService tenantLoginChallengeService;
    private final AppClock appClock;

    private final AuthEventAuditService authEventAuditService;

    private static String normalizeEmailRequired(String raw) {
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
    private static SystemRoleName toSystemRoleOrNull(TenantRole tenantRole) {
        if (tenantRole == null) return null;

        // Ajuste os "case" exatamente aos nomes do seu enum TenantRole.
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

    /**
     * POST /api/tenant/auth/login
     *
     * email + password
     * - se 1 tenant válido (e senha ok): JWT
     * - se >1:
     *      - valida senha em cada tenant candidato
     *      - se validar em 0: BadCredentials
     *      - se validar em 1: JWT direto
     *      - se validar em >1: TENANT_SELECTION_REQUIRED (challenge contém SOMENTE allowedAccountIds)
     */
    public TenantLoginResult loginInit(TenantLoginInitCommand cmd) {

        if (cmd == null) throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.email())) throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        if (!StringUtils.hasText(cmd.password())) throw new ApiException("INVALID_LOGIN", "password é obrigatório", 400);

        final String email = normalizeEmailRequired(cmd.email());
        final String password = cmd.password();

        authEventAuditService.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_INIT,
                AuditOutcome.ATTEMPT,
                email,
                null,
                null,
                null,
                "{\"stage\":\"init\"}"
        );

        try {
            // PUBLIC — descobre quais contas (tenants) têm esse email cadastrado
            List<LoginIdentityRow> identities = publicExecutor.inPublic(() ->
                    loginIdentityResolver.findTenantAccountsByEmail(email)
            );

            if (identities == null || identities.isEmpty()) {
                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                        "{\"reason\":\"no_candidates\"}");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            LinkedHashSet<Long> candidateAccountIds = identities.stream()
                    .map(LoginIdentityRow::accountId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (candidateAccountIds.isEmpty()) {
                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                        "{\"reason\":\"empty_candidate_ids\"}");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            // Se só tem 1 conta, autentica direto
            if (candidateAccountIds.size() == 1) {
                Long accountId = candidateAccountIds.iterator().next();
                AccountSnapshot account = accountResolver.resolveActiveAccountById(accountId);

                TenantLoginResult result = doTenantAuthentication(account, email, password);

                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.SUCCESS, email, null, accountId, account.schemaName(),
                        "{\"mode\":\"single_tenant\"}");

                return result;
            }

            // MULTI: validar password em cada tenant candidato
            LinkedHashSet<Long> allowedAccountIds = new LinkedHashSet<>();

            for (Long accountId : candidateAccountIds) {
                AccountSnapshot account;
                try {
                    account = accountResolver.resolveActiveAccountById(accountId);
                } catch (Exception ex) {
                    continue;
                }

                boolean ok = verifyPasswordInTenant(account, email, password);
                if (ok) allowedAccountIds.add(accountId);
            }

            if (allowedAccountIds.isEmpty()) {
                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                        "{\"reason\":\"bad_credentials_multi_tenant\"}");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            // Se só 1 tenant validou senha, entra direto nele
            if (allowedAccountIds.size() == 1) {
                Long accountId = allowedAccountIds.iterator().next();
                AccountSnapshot account = accountResolver.resolveActiveAccountById(accountId);

                TenantLoginResult result = doTenantAuthentication(account, email, password);

                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.SUCCESS, email, null, accountId, account.schemaName(),
                        "{\"mode\":\"multi_tenant_but_single_match\"}");

                return result;
            }

            // >1 tenant validou: cria challenge SOMENTE com allowedAccountIds
            UUID challengeId = tenantLoginChallengeService.createChallenge(email, allowedAccountIds);

            List<TenantSelectionOptionData> details = new ArrayList<>();
            for (Long id : allowedAccountIds) {
                details.add(tryResolveOption(id));
            }

            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.TENANT_SELECTION_REQUIRED, AuditOutcome.SUCCESS, email, null, null, null,
                    "{\"challengeId\":\"" + challengeId + "\",\"candidates\":" + allowedAccountIds.size() + "}");

            return new TenantLoginResult.TenantSelectionRequired(
                    challengeId.toString(),
                    details
            );

        } catch (BadCredentialsException e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"bad_credentials\"}");
            throw e;
        } catch (ApiException e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"api_exception\",\"code\":\"" + e.getError() + "\"}");
            throw e;
        } catch (Exception e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"unexpected\"}");
            throw e;
        }
    }

    public JwtResult loginConfirm(TenantLoginConfirmCommand cmd) {

        if (cmd == null) throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.challengeId())) throw new ApiException("INVALID_CHALLENGE", "challengeId é obrigatório", 400);

        UUID challengeId;
        try {
            challengeId = UUID.fromString(cmd.challengeId());
        } catch (Exception e) {
            throw new ApiException("INVALID_CHALLENGE", "challengeId inválido", 400);
        }

        TenantLoginChallenge challenge = tenantLoginChallengeService.requireValid(challengeId);
        final String email = challenge.getEmail();

        authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.ATTEMPT, email, null, null, null,
                "{\"challengeId\":\"" + challengeId + "\"}");

        Long accountId = cmd.accountId();
        String slug = StringUtils.hasText(cmd.slug()) ? cmd.slug().trim() : null;

        if (accountId == null && slug == null) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"missing_selection\"}");
            throw new ApiException("INVALID_SELECTION", "Informe accountId ou slug", 400);
        }

        AccountSnapshot account = (accountId != null)
                ? accountResolver.resolveActiveAccountById(accountId)
                : accountResolver.resolveActiveAccountBySlug(slug);

        if (account == null || account.id() == null) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"account_not_found\"}");
            throw new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
        }

        Set<Long> allowedAccountIds = challenge.candidateAccountIds();
        if (allowedAccountIds == null || !allowedAccountIds.contains(account.id())) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.FAILURE, email, null, account.id(), account.schemaName(),
                    "{\"reason\":\"account_not_in_challenge\"}");
            throw new ApiException("INVALID_SELECTION", "Conta não pertence ao challenge", 400);
        }

        tenantLoginChallengeService.markUsed(challenge);

        JwtResult jwt = issueJwtForAccountAndEmail(account, email);

        authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS, email, jwt.userId(), account.id(), account.schemaName(),
                "{\"mode\":\"challenge_confirm\"}");

        return jwt;
    }

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

            SystemRoleName role = toSystemRoleOrNull(user.getRole());

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

    // =========================================================
    // Helpers
    // =========================================================

    private boolean verifyPasswordInTenant(AccountSnapshot account, String email, String password) {
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

    private TenantSelectionOptionData tryResolveOption(Long accountId) {
        if (accountId == null) {
            return new TenantSelectionOptionData(null, "Conta", null);
        }

        try {
            AccountSnapshot a = accountResolver.resolveActiveAccountById(accountId);
            if (a == null) {
                return new TenantSelectionOptionData(accountId, "Conta " + accountId, null);
            }
            return new TenantSelectionOptionData(a.id(), a.displayName(), a.slug());
        } catch (ApiException ex) {
            return new TenantSelectionOptionData(accountId, "Conta " + accountId, null);
        } catch (Exception ex) {
            return new TenantSelectionOptionData(accountId, "Conta " + accountId, null);
        }
    }

    private TenantLoginResult doTenantAuthentication(AccountSnapshot account, String email, String password) {
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

    private JwtResult issueJwtForAccountAndEmail(AccountSnapshot account, String email) {
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
