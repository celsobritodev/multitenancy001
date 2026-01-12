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
    private final JwtTokenProvider jwtTokenProvider;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    public JwtResponse loginSuperAdmin(ControlPlaneAdminLoginRequest controlPlaneAdminLoginRequest) {


        TenantContext.clear();

        ControlPlaneUser user = controlPlaneUserRepository
                .findByUsernameAndDeletedFalse(controlPlaneAdminLoginRequest.username())
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Super admin não encontrado",
                        404
                ));

        // 🔒 Regras de negócio
        if (user.isSuspendedByAccount() ) {
            throw new ApiException(
                    "ACCESS_DENIED",
                    "Usuário não autorizado",
                    403
            );
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        controlPlaneAdminLoginRequest.username(),
                        controlPlaneAdminLoginRequest.password()
                )
        );

        String accessToken = jwtTokenProvider.generateControlPlaneToken(
                authentication,
                user.getAccount().getId(),
                "public"
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
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
