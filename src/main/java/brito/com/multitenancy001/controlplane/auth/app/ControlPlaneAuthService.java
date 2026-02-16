package brito.com.multitenancy001.controlplane.auth.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.controlplane.auth.app.command.ControlPlaneAdminLoginCommand;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.integration.audit.ControlPlaneAuthEventAuditIntegrationService;
import brito.com.multitenancy001.integration.auth.ControlPlaneJwtIntegrationService;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicSchemaExecutor;
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
    private final ControlPlaneJwtIntegrationService jwtIntegration;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final PublicSchemaExecutor publicExecutor;

    private final LoginIdentityResolver loginIdentityResolver;

    private final ControlPlaneAuthEventAuditIntegrationService authAudit;
    private final AppClock appClock;

    public JwtResult loginControlPlaneUser(ControlPlaneAdminLoginCommand cmd) {

        if (cmd == null) throw new ApiException(ApiErrorCode.INVALID_LOGIN, "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.email())) throw new ApiException(ApiErrorCode.INVALID_LOGIN, "email é obrigatório", 400);
        if (!StringUtils.hasText(cmd.password())) throw new ApiException(ApiErrorCode.INVALID_LOGIN, "password é obrigatório", 400);

        final String emailNorm = EmailNormalizer.normalizeOrNull(cmd.email());
        if (emailNorm == null) throw new ApiException(ApiErrorCode.INVALID_LOGIN, "email inválido", 400);

        final String password = cmd.password();

        auditAttempt(emailNorm, "{\"stage\":\"init\",\"mode\":\"password\"}");

        try {
            return publicExecutor.inPublic(() -> {

                // (1) Resolve identity -> subject_id (CP user id)
                Long cpUserId = loginIdentityResolver.resolveControlPlaneUserIdByEmail(emailNorm);
                if (cpUserId == null) {
                    auditDenied(emailNorm, "{\"reason\":\"identity_not_found\"}");
                    throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário de plataforma não encontrado", 404);
                }

                // (2) Carrega o usuário CP por ID (não por email)
                ControlPlaneUser user = controlPlaneUserRepository
                        .findByIdAndDeletedFalse(cpUserId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário de plataforma não encontrado", 404));

                Long accountId = user.getAccount().getId();

                // (3) Status checks
                Instant now = appClock.instant();

                if (!user.isEnabled()) {
                    auditDenied(emailNorm, "{\"reason\":\"user_not_enabled\"}");
                    throw new ApiException(ApiErrorCode.ACCESS_DENIED, "Usuário não autorizado", 403);
                }

                if (!user.isEnabledForLogin(now)) {
                    auditDenied(emailNorm, "{\"reason\":\"user_not_enabled_for_login\"}");
                    throw new ApiException(ApiErrorCode.ACCESS_DENIED, "Usuário não autorizado", 403);
                }

                // (4) Autentica
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(emailNorm, password)
                );

                String accessToken = jwtIntegration.generateControlPlaneToken(authentication, accountId, DEFAULT_SCHEMA);

                String refreshToken = jwtIntegration.generateRefreshToken(user.getEmail(), DEFAULT_SCHEMA, accountId);

                // (5) last_login + audit
                user.markLastLogin(now);
                controlPlaneUserRepository.save(user);

                SystemRoleName role = SystemRoleName.fromString(user.getRole() == null ? null : user.getRole().name());

                auditSuccess(emailNorm, "{\"stage\":\"success\"}");

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
            auditDenied(emailNorm, "{\"reason\":\"bad_credentials\"}");
            throw e;
        }
    }

    private void auditAttempt(String emailNorm, String detailsJson) {
        authAudit.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGIN_INIT,
                AuditOutcome.ATTEMPT,
                emailNorm,
                null,
                null,
                DEFAULT_SCHEMA,
                detailsJson
        );
    }

    private void auditDenied(String emailNorm, String detailsJson) {
        authAudit.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGIN_DENIED,
                AuditOutcome.DENIED,
                emailNorm,
                null,
                null,
                DEFAULT_SCHEMA,
                detailsJson
        );
    }

    private void auditSuccess(String emailNorm, String detailsJson) {
        authAudit.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGIN_SUCCESS,
                AuditOutcome.SUCCESS,
                emailNorm,
                null,
                null,
                DEFAULT_SCHEMA,
                detailsJson
        );
    }
}
