package brito.com.multitenancy001.controlplane.auth.app;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.auth.app.command.ControlPlaneAdminLoginCommand;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.integration.audit.ControlPlaneAuthEventAuditIntegrationService;
import brito.com.multitenancy001.integration.auth.ControlPlaneJwtIntegrationService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicSchemaExecutor;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityFinder;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de login do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar request de login.</li>
 *   <li>Resolver identidade via login_identities.</li>
 *   <li>Validar status do usuário (enabled / habilitado para login).</li>
 *   <li>Autenticar via AuthenticationManager.</li>
 *   <li>Emitir access token + refresh token.</li>
 *   <li>Registrar refresh session server-side.</li>
 *   <li>Auditar tentativa / sucesso / negação sem JSON manual.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneAuthService {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    private final AuthenticationManager authenticationManager;
    private final ControlPlaneJwtIntegrationService controlPlaneJwtIntegrationService;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final PublicSchemaExecutor publicSchemaExecutor;
    private final LoginIdentityFinder loginIdentityResolver;
    private final ControlPlaneAuthEventAuditIntegrationService controlPlaneAuthEventAuditIntegrationService;
    private final AppClock appClock;
    private final AuthRefreshSessionService authRefreshSessionService;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Efetua login do usuário do Control Plane e retorna access+refresh tokens.
     *
     * @param cmd comando de login (email/senha)
     * @return JwtResult com tokens e claims essenciais
     */
    public JwtResult loginControlPlaneUser(ControlPlaneAdminLoginCommand cmd) {
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "Requisição inválida", 400);
        }
        if (!StringUtils.hasText(cmd.email())) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "email é obrigatório", 400);
        }
        if (!StringUtils.hasText(cmd.password())) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "password é obrigatório", 400);
        }

        final String emailNorm = EmailNormalizer.normalizeOrNull(cmd.email());
        if (emailNorm == null) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "email inválido", 400);
        }

        final String password = cmd.password();

        auditAttempt(emailNorm, m(
                "stage", "init",
                "mode", "password"
        ));

        try {
            return publicSchemaExecutor.inPublic(() -> {
                Long cpUserId = loginIdentityResolver.resolveControlPlaneUserIdByEmail(emailNorm);
                if (cpUserId == null) {
                    auditDenied(emailNorm, m("reason", "identity_not_found"));
                    throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário de plataforma não encontrado", 404);
                }

                ControlPlaneUser user = controlPlaneUserRepository
                        .findByIdAndDeletedFalse(cpUserId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.USER_NOT_FOUND,
                                "Usuário de plataforma não encontrado",
                                404
                        ));

                Long accountId = user.getAccount().getId();
                Instant now = appClock.instant();

                if (!user.isEnabled()) {
                    auditDenied(emailNorm, m("reason", "user_not_enabled"));
                    throw new ApiException(ApiErrorCode.ACCESS_DENIED, "Usuário não autorizado", 403);
                }

                if (!user.isEnabledForLogin(now)) {
                    auditDenied(emailNorm, m("reason", "user_not_enabled_for_login"));
                    throw new ApiException(ApiErrorCode.ACCESS_DENIED, "Usuário não autorizado", 403);
                }

                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(emailNorm, password)
                );

                String accessToken = controlPlaneJwtIntegrationService.generateControlPlaneToken(
                        authentication,
                        accountId,
                        DEFAULT_SCHEMA,
                        user.getId()
                );

                String refreshToken = controlPlaneJwtIntegrationService.generateRefreshToken(
                        user.getEmail(),
                        DEFAULT_SCHEMA,
                        accountId
                );

                authRefreshSessionService.onRefreshIssued(
                        brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain.CONTROLPLANE,
                        accountId,
                        user.getId(),
                        null,
                        refreshToken
                );

                user.markLastLogin(now);
                controlPlaneUserRepository.save(user);

                SystemRoleName role = SystemRoleName.fromString(
                        user.getRole() == null ? null : user.getRole().name()
                );

                auditSuccess(emailNorm, m(
                        "stage", "success",
                        "userId", user.getId(),
                        "accountId", accountId
                ));

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
        } catch (BadCredentialsException ex) {
            auditDenied(emailNorm, m("reason", "bad_credentials"));
            throw ex;
        }
    }

    /**
     * Audita tentativa de login.
     *
     * @param emailNorm email normalizado
     * @param details detalhes estruturados
     */
    private void auditAttempt(String emailNorm, Map<String, Object> details) {
        controlPlaneAuthEventAuditIntegrationService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGIN_INIT,
                AuditOutcome.ATTEMPT,
                emailNorm,
                null,
                null,
                DEFAULT_SCHEMA,
                toJson(details)
        );
    }

    /**
     * Audita negação de login.
     *
     * @param emailNorm email normalizado
     * @param details detalhes estruturados
     */
    private void auditDenied(String emailNorm, Map<String, Object> details) {
        controlPlaneAuthEventAuditIntegrationService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGIN_DENIED,
                AuditOutcome.DENIED,
                emailNorm,
                null,
                null,
                DEFAULT_SCHEMA,
                toJson(details)
        );
    }

    /**
     * Audita sucesso de login.
     *
     * @param emailNorm email normalizado
     * @param details detalhes estruturados
     */
    private void auditSuccess(String emailNorm, Map<String, Object> details) {
        controlPlaneAuthEventAuditIntegrationService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGIN_SUCCESS,
                AuditOutcome.SUCCESS,
                emailNorm,
                null,
                null,
                DEFAULT_SCHEMA,
                toJson(details)
        );
    }

    /**
     * Monta mapa ordenado a partir de pares chave/valor.
     *
     * @param kv pares chave/valor
     * @return mapa ordenado
     */
    private Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (kv == null) {
            return map;
        }

        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object key = kv[i];
            Object value = kv[i + 1];
            if (key != null) {
                map.put(String.valueOf(key), value);
            }
        }

        return map;
    }

    /**
     * Serializa details estruturados para JSON.
     *
     * @param details detalhes estruturados
     * @return json serializado
     */
    private String toJson(Map<String, Object> details) {
        return details == null ? null : jsonDetailsMapper.toJsonNode(details).toString();
    }
}