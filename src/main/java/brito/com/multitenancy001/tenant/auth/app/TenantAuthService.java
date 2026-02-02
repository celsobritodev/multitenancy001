package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.infrastructure.publicschema.AccountResolver;
import brito.com.multitenancy001.infrastructure.publicschema.AccountSnapshot;
import brito.com.multitenancy001.infrastructure.publicschema.LoginIdentityResolver;
import brito.com.multitenancy001.infrastructure.publicschema.LoginIdentityRow;
import brito.com.multitenancy001.infrastructure.publicschema.auth.TenantLoginChallenge;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.SecurityConstants;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginConfirmCommand;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginInitCommand;
import brito.com.multitenancy001.tenant.auth.app.dto.AccountSelectionOptionData;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginResult;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantAuthService {

    private static final String INVALID_USER_MSG = "usuario ou senha invalidos";

    private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    private final AccountResolver accountResolver;
    private final LoginIdentityResolver loginIdentityResolver;

    private final TenantUserRepository tenantUserRepository;
    private final PublicExecutor publicExecutor;
    private final TenantExecutor tenantExecutor;

    private final TenantLoginChallengeService tenantLoginChallengeService;
    private final AppClock appClock;

    private LocalDateTime now() { return appClock.now(); }

    private static String normalizeEmailRequired(String raw) {
        String email = EmailNormalizer.normalizeOrNull(raw);
        if (!StringUtils.hasText(email)) {
            throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        }
        return email;
    }

    private static SystemRoleName toSystemRoleOrNull(Object tenantRoleEnum) {
        if (tenantRoleEnum == null) return null;
        return SystemRoleName.fromString(tenantRoleEnum.toString());
    }

    /**
     * 1) INIT LOGIN
     * email + password
     * - se 1 conta: JWT
     * - se >1 conta: ACCOUNT_SELECTION_REQUIRED
     */
    public TenantLoginResult loginInit(TenantLoginInitCommand cmd) {

        if (cmd == null) throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.email())) throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        if (!StringUtils.hasText(cmd.password())) throw new ApiException("INVALID_LOGIN", "password é obrigatório", 400);

        final String email = normalizeEmailRequired(cmd.email());
        final String password = cmd.password();

        // PUBLIC — descobre quais contas (tenants) têm esse email cadastrado
        List<LoginIdentityRow> candidates = publicExecutor.run(() ->
                loginIdentityResolver.findTenantAccountsByEmail(email)
        );

        if (candidates == null || candidates.isEmpty()) {
            throw new BadCredentialsException(INVALID_USER_MSG);
        }

        // accountIds candidatos (distinct + preserva ordem)
        LinkedHashSet<Long> candidateAccountIds = candidates.stream()
                .map(LoginIdentityRow::accountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (candidateAccountIds.isEmpty()) {
            throw new BadCredentialsException(INVALID_USER_MSG);
        }

        // Se só tem 1 conta, autentica direto
        if (candidateAccountIds.size() == 1) {
            Long accountId = candidateAccountIds.iterator().next();

            AccountSnapshot account = accountResolver.resolveActiveAccountById(accountId);

            return doTenantAuthentication(account, email, password);
        }

        // Se tem >1 conta, cria challenge e devolve candidatos (account selection)
        UUID challengeId = tenantLoginChallengeService.createChallenge(email, candidateAccountIds);

        // você não tem findAllByIds; então resolvemos 1 a 1 com fallback seguro
        List<AccountSelectionOptionData> selection = new ArrayList<>();
        for (Long id : candidateAccountIds) {
            AccountSelectionOptionData option = tryResolveOption(id);
            selection.add(option);
        }

        return new TenantLoginResult.AccountSelectionRequired(
                challengeId.toString(),
                selection
        );
    }

    /**
     * 2) CONFIRM LOGIN
     * challengeId + (accountId OU slug) => JWT
     */
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

        Long accountId = cmd.accountId();
        String slug = StringUtils.hasText(cmd.slug()) ? cmd.slug().trim() : null;

        if (accountId == null && slug == null) {
            throw new ApiException("INVALID_SELECTION", "Informe accountId ou slug", 400);
        }

        // resolve account via seus métodos existentes
        AccountSnapshot account;
        if (accountId != null) {
            account = accountResolver.resolveActiveAccountById(accountId);
        } else {
            account = accountResolver.resolveActiveAccountBySlug(slug);
        }

        // valida que a conta selecionada estava no challenge
        if (account == null || account.id() == null) {
            throw new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
        }

        // ✅ AJUSTE: seu entity expõe candidateAccountIds() (não getCandidateAccountIds())
        Set<Long> allowedAccountIds = challenge.candidateAccountIds();
        if (allowedAccountIds == null || !allowedAccountIds.contains(account.id())) {
            throw new ApiException("INVALID_SELECTION", "Conta não pertence ao challenge", 400);
        }

        // marca challenge como usado (anti replay)
        tenantLoginChallengeService.markUsed(challenge);

        final String email = challenge.getEmail();

        return issueJwtForAccountAndEmail(account, email);
    }

    /**
     * 3) REFRESH — retorna novo accessToken, reutilizando refreshToken
     */
    public JwtResult refresh(String refreshToken) {

        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken é obrigatório", 400);
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        String authDomain = jwtTokenProvider.getAuthDomain(refreshToken);
        if (!SecurityConstants.AuthDomains.REFRESH.equals(authDomain)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(refreshToken);
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        String email = EmailNormalizer.normalizeOrNull(jwtTokenProvider.getEmailFromToken(refreshToken));
        if (!StringUtils.hasText(email)) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido", 401);
        }

        Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);
        if (accountId == null) {
            throw new ApiException("INVALID_REFRESH", "refreshToken inválido (accountId ausente)", 401);
        }

        return tenantExecutor.run(tenantSchema, () -> {

            TenantUser user = tenantUserRepository
                    .findByEmailAndAccountIdAndDeletedFalse(email, accountId)
                    .orElseThrow(() -> new ApiException("INVALID_REFRESH", "refreshToken inválido", 401));

            if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
                throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
            }

            // grava last_login também no refresh
            tenantUserRepository.updateLastLogin(user.getId(), now());

            var authorities = AuthoritiesFactory.forTenant(user);

            AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                    user,
                    tenantSchema,
                    now(),
                    authorities
            );

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );

            String newAccessToken = jwtTokenProvider.generateTenantToken(
                    authentication,
                    accountId,
                    tenantSchema
            );

            SystemRoleName role = toSystemRoleOrNull(user.getRole());

            // reusa o MESMO refreshToken
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

    // =========================================================
    // Helpers (mantêm a DDD/layered)
    // =========================================================

    private AccountSelectionOptionData tryResolveOption(Long accountId) {
        if (accountId == null) {
            return new AccountSelectionOptionData(null, "Conta", null);
        }

        try {
            AccountSnapshot a = accountResolver.resolveActiveAccountById(accountId);
            if (a == null) {
                return new AccountSelectionOptionData(accountId, "Conta " + accountId, null);
            }
            return new AccountSelectionOptionData(a.id(), a.displayName(), a.slug());
        } catch (ApiException ex) {
            return new AccountSelectionOptionData(accountId, "Conta " + accountId, null);
        } catch (Exception ex) {
            return new AccountSelectionOptionData(accountId, "Conta " + accountId, null);
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

                tenantUserRepository.updateLastLogin(user.getId(), now());

                var authorities = AuthoritiesFactory.forTenant(user);

                AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                        user,
                        tenantSchema,
                        now(),
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

                return new TenantLoginResult.LoginSuccess(jwt);
            });
        } catch (BadCredentialsException e) {
            throw e;
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException(INVALID_USER_MSG);
        } catch (InternalAuthenticationServiceException e) {
            throw new BadCredentialsException(INVALID_USER_MSG);
        } catch (Exception e) {
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

            tenantUserRepository.updateLastLogin(user.getId(), now());

            var authorities = AuthoritiesFactory.forTenant(user);

            AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                    user,
                    tenantSchema,
                    now(),
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
