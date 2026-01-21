package brito.com.multitenancy001.tenant.application.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

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

@Service
@RequiredArgsConstructor
public class TenantAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final AccountResolver accountResolver;
    private final TenantUserRepository tenantUserRepository;

    private final PublicExecutor publicExecutor;
    private final TenantExecutor tenantExecutor;

    public JwtResponse loginTenant(TenantLoginRequest tenantLoginRequest) {

        // 1) PUBLIC — resolve conta 
        AccountSnapshot account = publicExecutor.run(() ->
                accountResolver.resolveActiveAccountBySlug(tenantLoginRequest.slug())
        );

        // 2) TENANT — roda no schema do tenant sem bind/clear manual
        return tenantExecutor.run(account.schemaName(), () -> {

            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    tenantLoginRequest.username(),
                                    tenantLoginRequest.password()
                            )
                    );

            TenantUser user = tenantUserRepository
                    .findByUsernameAndAccountId(tenantLoginRequest.username(), account.id())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
                throw new ApiException("USER_INACTIVE", "Usuário inativo", 403);
            }

            String accessToken = tokenProvider.generateTenantToken(
                    authentication,
                    account.id(),
                    account.schemaName()
            );

            String refreshToken = tokenProvider.generateRefreshToken(
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
}
