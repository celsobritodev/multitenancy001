package brito.com.multitenancy001.controlplane.auth.app;

import brito.com.multitenancy001.controlplane.auth.app.command.ControlPlaneAdminLoginCommand;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEventAuditService;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityResolver;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ControlPlaneAuthService {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final PublicExecutor publicExecutor;

    private final LoginIdentityResolver loginIdentityResolver;

    private final AuthEventAuditService authEventAuditService;
    private final AppClock appClock;

    public JwtResult loginControlPlaneUser(ControlPlaneAdminLoginCommand cmd) {

        if (cmd == null) throw new ApiException("INVALID_LOGIN", "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.email())) throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        if (!StringUtils.hasText(cmd.password())) throw new ApiException("INVALID_LOGIN", "password é obrigatório", 400);

        final String emailNorm = EmailNormalizer.normalizeOrNull(cmd.email());
        if (emailNorm == null) throw new ApiException("INVALID_LOGIN", "email inválido", 400);

        final String password = cmd.password();

        authEventAuditService.record(
                "controlplane",
                "LOGIN_INIT",
                "ATTEMPT",
                emailNorm,
                null,
                null,
                DEFAULT_SCHEMA,
                "{\"stage\":\"init\",\"mode\":\"password\"}"
        );

        try {
            return publicExecutor.runInPublicSchema(() -> {

                // =========================================================
                // (1) Resolve identity -> subject_id (CP user id)
                // =========================================================
                Long cpUserId = loginIdentityResolver.resolveControlPlaneUserIdByEmail(emailNorm);
                if (cpUserId == null) {
                    authEventAuditService.record(
                            "controlplane",
                            "LOGIN_DENIED",
                            "DENIED",
                            emailNorm,
                            null,
                            null,
                            DEFAULT_SCHEMA,
                            "{\"reason\":\"identity_not_found\"}"
                    );
                    // 404 para não vazar existência
                    throw new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404);
                }

                // =========================================================
                // (2) Carrega o usuário CP por ID (não por email)
                // =========================================================
                ControlPlaneUser user = controlPlaneUserRepository
                        .findByIdAndDeletedFalse(cpUserId)
                        .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário de plataforma não encontrado", 404));

                Long accountId = user.getAccount().getId();

                // =========================================================
                // (3) Status checks (semântico)
                // =========================================================
                Instant now = appClock.instant();

                if (!user.isEnabled()) {
                    authEventAuditService.record(
                            "controlplane",
                            "LOGIN_DENIED",
                            "DENIED",
                            emailNorm,
                            user.getId(),
                            accountId,
                            DEFAULT_SCHEMA,
                            "{\"reason\":\"not_enabled\"}"
                    );
                    throw new ApiException("ACCESS_DENIED", "Usuário não autorizado", 403);
                }

                if (!user.isEnabledForLogin(now)) {
                    authEventAuditService.record(
                            "controlplane",
                            "LOGIN_DENIED",
                            "DENIED",
                            emailNorm,
                            user.getId(),
                            accountId,
                            DEFAULT_SCHEMA,
                            "{\"reason\":\"locked\"}"
                    );
                    throw new ApiException("ACCESS_DENIED", "Usuário não autorizado", 403);
                }

                // =========================================================
                // (4) Autentica com principal=email, mas UserDetailsService
                // agora resolve por identity -> userId -> user
                // =========================================================
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(emailNorm, password)
                );

                String accessToken = jwtTokenProvider.generateControlPlaneToken(
                        authentication,
                        accountId,
                        DEFAULT_SCHEMA
                );

                // refresh token pode seguir usando o email "atual" do perfil
                String refreshToken = jwtTokenProvider.generateRefreshToken(
                        user.getEmail(),
                        DEFAULT_SCHEMA,
                        accountId
                );

                // =========================================================
                // (5) last_login + audit
                // =========================================================
                user.markLastLogin(now);
                controlPlaneUserRepository.save(user);

                SystemRoleName role = SystemRoleName.fromString(user.getRole() == null ? null : user.getRole().name());

                authEventAuditService.record(
                        "controlplane",
                        "LOGIN_SUCCESS",
                        "SUCCESS",
                        emailNorm,
                        user.getId(),
                        accountId,
                        DEFAULT_SCHEMA,
                        "{\"mode\":\"password\",\"identity\":\"login_identities\",\"subject\":\"CONTROLPLANE_USER\"}"
                );

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
        } catch (BadCredentialsException e) {
            authEventAuditService.record(
                    "controlplane",
                    "LOGIN_FAILURE",
                    "FAILURE",
                    emailNorm,
                    null,
                    null,
                    DEFAULT_SCHEMA,
                    "{\"reason\":\"bad_credentials\"}"
            );
            throw e;
        }
    }
}
