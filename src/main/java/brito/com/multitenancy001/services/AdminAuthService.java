package brito.com.multitenancy001.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.dtos.JwtResponse;
import brito.com.multitenancy001.dtos.SuperAdminLoginRequest;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.multitenancy.TenantContext;
import brito.com.multitenancy001.platform.domain.user.PlatformUser;
import brito.com.multitenancy001.repositories.publicdb.PlatformUserRepository;
import brito.com.multitenancy001.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final PlatformUserRepository userAccountRepository;

    public JwtResponse loginSuperAdmin(SuperAdminLoginRequest request) {

        // 🔥 SUPER ADMIN SEMPRE NO PUBLIC
        TenantContext.unbindTenant();

        PlatformUser user = userAccountRepository
                .findByUsernameAndDeletedFalse(request.username())
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Super admin não encontrado",
                        404
                ));

        // 🔒 Regras de negócio
        if (!user.isActive() || !user.getRole().isPlatformRole()) {
            throw new ApiException(
                    "ACCESS_DENIED",
                    "Usuário não autorizado",
                    403
            );
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        String accessToken = tokenProvider.generateAccountToken(
                authentication,
                user.getAccount().getId(),
                "public"
        );

        String refreshToken = tokenProvider.generateRefreshToken(
                user.getUsername(),
                "public"
        );

        return new JwtResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getAccount().getId(),
                "public"
        );
    }
}
