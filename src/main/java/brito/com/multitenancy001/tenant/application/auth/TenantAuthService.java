package brito.com.multitenancy001.tenant.application.auth;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.account.AccountResolver;
import brito.com.multitenancy001.shared.account.AccountSnapshot;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantLoginRequest;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.persistence.user.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TenantAuthService {

    private static final String INVALID_USER_MSG = "usuario ou senha invalidos";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountResolver accountResolver;
    private final TenantUserRepository tenantUserRepository;

    private final PublicExecutor publicExecutor;
    private final TenantExecutor tenantExecutor;

    public JwtResponse loginTenant(TenantLoginRequest req) {

        // validações mínimas (request ruim = 400)
        if (req == null) throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        if (!StringUtils.hasText(req.slug())) throw new ApiException("INVALID_SLUG", "slug é obrigatório", 400);
        if (!StringUtils.hasText(req.username())) throw new ApiException("INVALID_LOGIN", "username é obrigatório", 400);
        if (!StringUtils.hasText(req.password())) throw new ApiException("INVALID_LOGIN", "password é obrigatório", 400);

        // normalização de login
        final String username = req.username().trim().toLowerCase();
        final String password = req.password();

        // 1) PUBLIC — resolve conta
        AccountSnapshot account = publicExecutor.run(() ->
                accountResolver.resolveActiveAccountBySlug(req.slug().trim())
        );

        // 2) TENANT — executa no schema do tenant
        return tenantExecutor.run(account.schemaName(), () -> {

            // ✅ autentica (qualquer falha vira INVALID_USER/401 via handler)
            Authentication authentication = authenticateOrInvalidUser(username, password);

            // ✅ carrega user no tenant para checar status e montar response
            // (não deixa "USER_NOT_FOUND" vazar)
            TenantUser user = tenantUserRepository
                    .findByUsernameAndAccountIdAndDeletedFalse(username, account.id())
                    .orElseThrow(() -> new BadCredentialsException(INVALID_USER_MSG));

            // status do user (inativo = 403)
            if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
                throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
            }

            String accessToken = jwtTokenProvider.generateTenantToken(
                    authentication,
                    account.id(),
                    account.schemaName()
            );

            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getUsername(),
                    account.schemaName()
            );

            return new JwtResponse(
                    accessToken,
                    refreshToken,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole().name(),
                    account.id(),
                    account.schemaName()
            );
        });
    }

    /**
     * Converte falhas de autenticação em BadCredentialsException (para virar INVALID_USER no handler).
     */
    private Authentication authenticateOrInvalidUser(String username, String password) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

        } catch (BadCredentialsException e) {
            // senha errada
            throw new BadCredentialsException(INVALID_USER_MSG);

        } catch (UsernameNotFoundException e) {
            // user inexistente (se escapar)
            throw new BadCredentialsException(INVALID_USER_MSG);

        } catch (InternalAuthenticationServiceException e) {
            // DaoAuthenticationProvider envolve UsernameNotFoundException aqui
            Throwable cause = e.getCause();
            if (cause instanceof UsernameNotFoundException) {
                throw new BadCredentialsException(INVALID_USER_MSG);
            }
            throw e; // erro real

        }
    }
}
