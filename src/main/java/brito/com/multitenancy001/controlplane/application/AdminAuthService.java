package brito.com.multitenancy001.controlplane.application;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.api.dto.auth.ControlPlaneAdminLoginRequest;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.context.TenantContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    public JwtResponse loginSuperAdmin(ControlPlaneAdminLoginRequest request) {

        // 🔥 SUPER ADMIN SEMPRE NO PUBLIC
        TenantContext.clear();

        ControlPlaneUser user = controlPlaneUserRepository
                .findByUsernameAndDeletedFalse(request.username())
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Super admin não encontrado",
                        404
                ));

        // 🔒 Regras de negócio
        if (user.isSuspendedByAccount() || !user.getRole().isControlPlaneRole()) {
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

        String accessToken = tokenProvider.generateControlPlaneToken(
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
