package brito.com.multitenancy001.controlplane.application;

import brito.com.multitenancy001.controlplane.api.dto.auth.ControlPlaneAdminLoginRequest;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ControlPlaneAuthService {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final PublicExecutor publicExecutor;

    public JwtResponse loginControlPlaneUser(ControlPlaneAdminLoginRequest req) {

        return publicExecutor.run(() -> {

            // 1) busca user (public)
            ControlPlaneUser user = controlPlaneUserRepository
                    .findByEmailAndDeletedFalse(req.email())
                    .orElseThrow(() -> new ApiException(
                            "USER_NOT_FOUND",
                            "Usuário de plataforma não encontrado",
                            404
                    ));

            // 2) regra: suspenso por conta -> bloqueia
            if (user.isSuspendedByAccount()) {
                throw new ApiException("ACCESS_DENIED", "Usuário não autorizado", 403);
            }

            // 3) autentica (senha)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password())
            );

            // 4) tokens
            String accessToken = jwtTokenProvider.generateControlPlaneToken(
                    authentication,
                    user.getAccount().getId(),
                    DEFAULT_SCHEMA
            );

            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getEmail(),
                    DEFAULT_SCHEMA,
                    user.getAccount().getId()
            );

            // ✅ TIPAGEM CORRETA
            SystemRoleName role =
                    SystemRoleName.fromString(user.getRole().name());

            return JwtResponse.forEmailLogin(
                    accessToken,
                    refreshToken,
                    user.getId(),
                    user.getEmail(),
                    role,
                    user.getAccount().getId(),
                    DEFAULT_SCHEMA
            );
        });
    }
}
