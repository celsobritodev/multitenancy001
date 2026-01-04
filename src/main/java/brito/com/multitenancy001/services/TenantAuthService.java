package brito.com.multitenancy001.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.dtos.JwtResponse;
import brito.com.multitenancy001.dtos.TenantLoginRequest;
import brito.com.multitenancy001.entities.tenant.TenantUser;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.multitenancy.TenantContext;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.repositories.publicdb.AccountRepository;
import brito.com.multitenancy001.repositories.tenant.TenantUserRepository;
import brito.com.multitenancy001.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final AccountRepository accountRepository;
    private final TenantUserRepository userTenantRepository;

    public JwtResponse loginTenant(TenantLoginRequest request) {

        // 1️⃣ PUBLIC — resolve conta
        TenantContext.unbindTenant();

        TenantAccount account = accountRepository
                .findBySlugAndDeletedFalse(request.slug())
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada",
                        404
                ));

        if (!account.isActive()) {
            throw new ApiException(
                    "ACCOUNT_INACTIVE",
                    "Conta inativa",
                    403
            );
        }

        // 2️⃣ TENANT — bind correto
        TenantContext.bindTenant(account.getSchemaName());

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.username(),
                                    request.password()
                            )
                    );

            TenantUser user = userTenantRepository
                    .findByUsernameAndAccountId(
                            request.username(),
                            account.getId()
                    )
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado",
                            404
                    ));

            if (!user.isActive() || user.isDeleted()) {
                throw new ApiException(
                        "USER_INACTIVE",
                        "Usuário inativo",
                        403
                );
            }

            String accessToken = tokenProvider.generateTenantToken(
                    authentication,
                    account.getId(),
                    account.getSchemaName()
            );

            String refreshToken = tokenProvider.generateRefreshToken(
                    user.getUsername(),
                    account.getSchemaName()
            );

            return new JwtResponse(
                    accessToken,
                    refreshToken,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole().name(),
                    account.getId(),
                    account.getSchemaName()
            );

        } finally {
            TenantContext.unbindTenant();
        }
    }
}
