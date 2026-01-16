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
public class ControlPlaneAuthService {

    private static final String DEFAULT_SCHEMA = "public";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    public JwtResponse loginControlPlaneUser(ControlPlaneAdminLoginRequest req) {

        TenantContext.clear();

        // 1) busca user (public)
        ControlPlaneUser user = controlPlaneUserRepository
                .findByUsernameAndDeletedFalse(req.username())
                .orElseThrow(() -> new ApiException(
                        "USER_NOT_FOUND",
                        "Usuário de plataforma não encontrado",
                        404
                ));

        // 2) regra: suspenso por conta -> bloqueia
        if (user.isSuspendedByAccount()) {
            throw new ApiException("ACCESS_DENIED", "Usuário não autorizado", 403);
        }

        // 3) autentica de fato (senha)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );

        // ✅ IMPORTANTE:
        // Não bloquear must_change_password no login.
        // O bloqueio de rotas deve acontecer no MustChangePasswordFilter,
        // permitindo apenas /api/admin/me/password.

        // 4) tokens
        String accessToken = jwtTokenProvider.generateControlPlaneToken(
                authentication,
                user.getAccount().getId(),
                DEFAULT_SCHEMA
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getUsername(),
                DEFAULT_SCHEMA
        );

        return new JwtResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getAccount().getId(),
                DEFAULT_SCHEMA
        );
    }
}
