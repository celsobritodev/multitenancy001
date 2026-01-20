package brito.com.multitenancy001.tenant.application.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.shared.account.AccountResolver;
import brito.com.multitenancy001.shared.account.AccountSnapshot;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
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

    public JwtResponse loginTenant(TenantLoginRequest tenantLoginRequest) {

        // 1️⃣ PUBLIC — resolve conta
        TenantContext.clear();

        AccountSnapshot account = accountResolver.resolveActiveAccountBySlug(tenantLoginRequest.slug());

        

        // 2️⃣ TENANT — bind correto
        TenantContext.bind(account.schemaName());

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    tenantLoginRequest.username(),
                                    tenantLoginRequest.password()
                            )
                    );

            TenantUser user = tenantUserRepository
                    .findByUsernameAndAccountId(
                            tenantLoginRequest.username(),
                            account.id()
                    )
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado",
                            404
                    ));


            if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
                throw new ApiException(
                        "USER_INACTIVE",
                        "Usuário inativo",
                        403
                );
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

        } finally {
            TenantContext.clear();
        }
    }
}
