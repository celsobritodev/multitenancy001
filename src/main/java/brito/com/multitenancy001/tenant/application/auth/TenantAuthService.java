package brito.com.multitenancy001.tenant.application.auth;

import brito.com.multitenancy001.infrastructure.publicschema.AccountResolver;
import brito.com.multitenancy001.infrastructure.publicschema.AccountSnapshot;
import brito.com.multitenancy001.infrastructure.publicschema.LoginIdentityResolver;
import brito.com.multitenancy001.infrastructure.publicschema.LoginIdentityRow;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantLoginRequest;
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

import java.util.ArrayList;
import java.util.List;

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

    public JwtResponse loginTenant(TenantLoginRequest req) {

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

        // 2) Se veio accountId, tenta só nele (2º passo do front)
        if (req.accountId() != null) {
            Long chosen = req.accountId();

            boolean exists = candidates.stream().anyMatch(r -> r.accountId().equals(chosen));
            if (!exists) {
                // evita vazar info / evita brute force de accountId
                throw new BadCredentialsException(INVALID_USER_MSG);
            }

            AccountSnapshot account = publicExecutor.run(() ->
                    accountResolver.resolveActiveAccountById(chosen)
            );

            return attemptLoginOnAccount(account, email, password);
        }

        // 3) Se NÃO veio accountId: tenta autenticar em cada tenant
        //    - se só 1 bater -> JWT direto
        //    - se várias baterem -> pede seleção
        //    - se nenhuma bater -> invalid
        List<SuccessfulTenantLogin> successes = new ArrayList<>();

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
                JwtResponse jwt = attemptLoginOnAccount(account, email, password);
                successes.add(new SuccessfulTenantLogin(account, jwt));
            } catch (BadCredentialsException ex) {
                // senha não bate nesse tenant -> ignora
            } catch (ApiException ex) {
                // usuário inativo nesse tenant -> ignora (ou decide se quer bloquear)
            }
        }

        if (successes.isEmpty()) {
            throw new BadCredentialsException(INVALID_USER_MSG);
        }

        if (successes.size() == 1) {
            return successes.get(0).jwt();
        }

        // várias contas com senha válida -> pede escolha
        List<TenantSelectionOption> options = successes.stream()
                .map(s -> new TenantSelectionOption(
                        s.account().id(),
                        s.account().displayName(),
                        s.account().slug()
                ))
                .toList();

        throw new ApiException(
                "TENANT_SELECTION_REQUIRED",
                "Selecione a empresa",
                409,
                options
        );
    }

    private JwtResponse attemptLoginOnAccount(AccountSnapshot account, String email, String password) {

        return tenantExecutor.run(account.schemaName(), () -> {

            Authentication authentication = authenticateOrInvalidUser(email, password);

            TenantUser user = tenantUserRepository
                    .findByEmailAndDeletedFalse(email)
                    .orElseThrow(() -> new BadCredentialsException(INVALID_USER_MSG));

            if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
                throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
            }

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

    private record SuccessfulTenantLogin(AccountSnapshot account, JwtResponse jwt) {}
}
