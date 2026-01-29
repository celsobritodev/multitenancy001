package brito.com.multitenancy001.tenant.application.auth;

import brito.com.multitenancy001.infrastructure.publicschema.AccountResolver;
import brito.com.multitenancy001.infrastructure.publicschema.AccountSnapshot;
import brito.com.multitenancy001.infrastructure.publicschema.LoginIdentityResolver;
import brito.com.multitenancy001.infrastructure.publicschema.LoginIdentityRow;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.persistence.auth.TenantLoginChallenge;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantLoginAmbiguousResponse;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantLoginConfirmRequest;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantLoginInitRequest;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantSelectionOption;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
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

    /**
     * 1) INIT LOGIN
     * email + password
     * - se 1 tenant: JWT
     * - se >1 tenant: challengeId + candidates (controller devolve 409)
     */
    public Object loginInit(TenantLoginInitRequest req) {

        if (req == null) throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        if (!StringUtils.hasText(req.email())) throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        if (!StringUtils.hasText(req.password())) throw new ApiException("INVALID_LOGIN", "password é obrigatório", 400);

        final String email = req.email().trim().toLowerCase();
        final String password = req.password();

        // 1) PUBLIC — descobre quais contas (tenants) têm esse email cadastrado
        List<LoginIdentityRow> candidates = publicExecutor.run(() ->
                loginIdentityResolver.findTenantAccountsByEmail(email)
        );

        if (candidates == null || candidates.isEmpty()) {
            throw new BadCredentialsException(INVALID_USER_MSG);
        }

        // 2) Valida credenciais em cada tenant e acumula somente os tenants onde bate
        List<AccountSnapshot> validAccounts = new ArrayList<>();

        for (LoginIdentityRow row : candidates) {

            AccountSnapshot account;
            try {
                account = publicExecutor.run(() ->
                        accountResolver.resolveActiveAccountById(row.accountId())
                );
            } catch (ApiException e) {
                // conta inativa/cancelada/etc -> ignora
                continue;
            }

            try {
                validateCredentialsOnAccount(account, email, password);
                validAccounts.add(account);
            } catch (BadCredentialsException ex) {
                // senha não bate nesse tenant -> ignora
            } catch (ApiException ex) {
                // usuário inativo nesse tenant -> ignora
            }
        }

        if (validAccounts.isEmpty()) {
            throw new BadCredentialsException(INVALID_USER_MSG);
        }

        if (validAccounts.size() == 1) {
            return issueJwtForAccount(validAccounts.get(0), email);
        }

        // 3) várias contas com senha válida -> cria challenge
        Set<Long> accountIds = validAccounts.stream()
                .map(AccountSnapshot::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        UUID challengeId = publicExecutor.run(() -> tenantLoginChallengeService.createChallenge(email, accountIds));

        List<TenantSelectionOption> options = validAccounts.stream()
                .map(a -> new TenantSelectionOption(a.id(), a.displayName(), a.slug()))
                .toList();

        return new TenantLoginAmbiguousResponse(
                "ACCOUNT_SELECTION_REQUIRED",
                "Selecione a empresa",
                challengeId.toString(),
                options
        );
    }

    /**
     * 2) CONFIRM LOGIN
     * challengeId + accountId
     * - valida challenge (expiração + não usado)
     * - valida accountId pertence aos candidates
     * - gera JWT sem pedir senha novamente
     */
    public JwtResponse loginConfirm(TenantLoginConfirmRequest req) {

        if (req == null) throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        if (!StringUtils.hasText(req.challengeId())) throw new ApiException("INVALID_CHALLENGE", "challengeId é obrigatório", 400);
        if (req.accountId() == null) throw new ApiException("INVALID_CHALLENGE", "accountId é obrigatório", 400);

        UUID challengeUuid;
        try {
            challengeUuid = UUID.fromString(req.challengeId().trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException("INVALID_CHALLENGE", "challengeId inválido", 401);
        }

        TenantLoginChallenge challenge = publicExecutor.run(() -> tenantLoginChallengeService.requireValid(challengeUuid));

        String email = challenge.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new ApiException("INVALID_CHALLENGE", "challengeId inválido ou expirado", 401);
        }

        Set<Long> allowed = challenge.candidateAccountIds();
        if (allowed == null || allowed.isEmpty() || !allowed.contains(req.accountId())) {
            throw new ApiException("INVALID_CHALLENGE", "challengeId inválido ou expirado", 401);
        }

        AccountSnapshot account = publicExecutor.run(() ->
                accountResolver.resolveActiveAccountById(req.accountId())
        );

        JwtResponse jwt = issueJwtForAccount(account, email);

        publicExecutor.run(() -> {
            tenantLoginChallengeService.markUsed(challenge);
            return null;
        });

        return jwt;
    }

    /**
     * Valida se email/senha estão corretos naquele tenant e se o usuário está ativo.
     * Não gera token.
     */
    private void validateCredentialsOnAccount(AccountSnapshot account, String email, String password) {

        tenantExecutor.run(account.schemaName(), () -> {

            authenticateOrInvalidUser(email, password);

            TenantUser user = tenantUserRepository
                    .findByEmailAndDeletedFalse(email)
                    .orElseThrow(() -> new BadCredentialsException(INVALID_USER_MSG));

            if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
                throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
            }

            return null;
        });
    }

    /**
     * Emite JWT sem precisar da senha.
     * - carrega TenantUser no schema
     * - cria Authentication com principal AuthenticatedUserContext
     * - gera access + refresh
     */
    private JwtResponse issueJwtForAccount(AccountSnapshot account, String email) {

        return tenantExecutor.run(account.schemaName(), () -> {

            TenantUser user = tenantUserRepository
                    .findByEmailAndDeletedFalse(email)
                    .orElseThrow(() -> new BadCredentialsException(INVALID_USER_MSG));

            if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
                throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
            }

            var authorities = AuthoritiesFactory.forTenant(user);

            AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                    user,
                    account.schemaName(),
                    now(),
                    authorities
            );

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );

            String accessToken = jwtTokenProvider.generateTenantToken(
                    authentication,
                    account.id(),
                    account.schemaName()
            );

            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getEmail(),
                    account.schemaName()
            );

            return new JwtResponse(
                    accessToken,
                    refreshToken,
                    user.getId(),
                    user.getEmail(),
                    user.getRole().name(),
                    account.id(),
                    account.schemaName()
            );
        });
    }

    private Authentication authenticateOrInvalidUser(String email, String password) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

        } catch (BadCredentialsException e) {
            throw new BadCredentialsException(INVALID_USER_MSG);

        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException(INVALID_USER_MSG);

        } catch (InternalAuthenticationServiceException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UsernameNotFoundException) {
                throw new BadCredentialsException(INVALID_USER_MSG);
            }
            throw e;
        }
    }
}
