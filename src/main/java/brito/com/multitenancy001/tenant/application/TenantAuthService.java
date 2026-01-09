package brito.com.multitenancy001.tenant.application;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
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
    private final AccountRepository accountRepository;
    private final TenantUserRepository tenantUserRepository;

    public JwtResponse loginTenant(TenantLoginRequest request) {

        // 1️⃣ PUBLIC — resolve conta
        TenantContext.clear();

        Account account = accountRepository
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
        TenantContext.bind(account.getSchemaName());

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.username(),
                                    request.password()
                            )
                    );

            TenantUser user = tenantUserRepository
                    .findByUsernameAndAccountId(
                            request.username(),
                            account.getId()
                    )
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário não encontrado",
                            404
                    ));

            if (user.isSuspendedByAccount() || user.isDeleted()) {
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
            TenantContext.clear();
        }
    }
}
