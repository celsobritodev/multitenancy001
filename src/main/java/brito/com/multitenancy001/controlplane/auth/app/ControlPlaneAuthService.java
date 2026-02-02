package brito.com.multitenancy001.controlplane.auth.app;

import brito.com.multitenancy001.controlplane.auth.app.command.ControlPlaneAdminLoginCommand;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ControlPlaneAuthService {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final PublicExecutor publicExecutor;

    public JwtResult loginControlPlaneUser(ControlPlaneAdminLoginCommand cmd) {

        if (cmd == null) throw new ApiException("INVALID_LOGIN", "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.email())) throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        if (!StringUtils.hasText(cmd.password())) throw new ApiException("INVALID_LOGIN", "password é obrigatório", 400);

        final String email = cmd.email().trim();
        final String password = cmd.password();

        return publicExecutor.runInPublicSchema(() -> {

            ControlPlaneUser user = controlPlaneUserRepository
                    .findByEmailAndDeletedFalse(email)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

            if (user.isSuspendedByAccount()) {
                throw new ApiException("ACCESS_DENIED", "Usuário não autorizado", 403);
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            Long accountId = user.getAccount().getId();

            String accessToken = jwtTokenProvider.generateControlPlaneToken(
                    authentication,
                    accountId,
                    DEFAULT_SCHEMA
            );

            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getEmail(),
                    DEFAULT_SCHEMA,
                    accountId
            );

            SystemRoleName role = SystemRoleName.fromString(user.getRole().name());

            return new JwtResult(
                    accessToken,
                    refreshToken,
                    user.getId(),
                    user.getEmail(),
                    role,
                    accountId,
                    DEFAULT_SCHEMA
            );
        });
    }
}
