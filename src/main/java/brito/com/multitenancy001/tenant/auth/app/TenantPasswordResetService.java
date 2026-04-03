package brito.com.multitenancy001.tenant.auth.app;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service (Tenant): Password Reset.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>O token contém email, tenantSchema e accountId.</li>
 *   <li>O reset com token executa no schema do tenant via {@link TenantExecutor}.</li>
 *   <li>Auditoria append-only no public schema.</li>
 *   <li>Details de auditoria sempre estruturados e serializados via {@link JsonDetailsMapper}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPasswordResetService {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantUserCommandService tenantUserCommandService;
    private final AccountResolver accountResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final TenantExecutor tenantExecutor;
    private final AppClock appClock;
    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Gera token de reset de senha para usuário tenant ativo.
     *
     * @param slug slug da account
     * @param email email do usuário
     * @return token de reset
     */
    public String generatePasswordResetToken(String slug, String email) {
        if (!StringUtils.hasText(slug)) {
            throw new ApiException(ApiErrorCode.INVALID_SLUG, "Slug é obrigatório", 400);
        }
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "Email é obrigatório", 400);
        }

        String normalizedEmail = email.trim().toLowerCase();

        securityAuditService.record(
                SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                AuditOutcome.ATTEMPT,
                null,
                null,
                normalizedEmail,
                null,
                null,
                null,
                toJson(Map.of("slug", slug.trim()))
        );

        AccountSnapshot account = accountResolver.resolveActiveAccountBySlug(slug);

        String tenantSchemaRaw = account.tenantSchema();
        if (!StringUtils.hasText(tenantSchemaRaw)) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_READY, "Conta sem schema", 409);
        }

        final String tenantSchema = tenantSchemaRaw.trim();

        try {
            String token = tenantExecutor.runInTenantSchema(tenantSchema, () -> {
                TenantUser user = tenantUserQueryService.getUserByEmail(normalizedEmail, account.id());

                String passwordResetToken = jwtTokenProvider.generatePasswordResetToken(
                        user.getEmail(),
                        tenantSchema,
                        account.id()
                );

                user.setPasswordResetToken(passwordResetToken);
                user.setPasswordResetExpires(appClock.instant().plus(Duration.ofHours(1)));

                tenantUserCommandService.save(tenantSchema, user);
                return passwordResetToken;
            });

            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                    AuditOutcome.SUCCESS,
                    null,
                    null,
                    normalizedEmail,
                    null,
                    account.id(),
                    tenantSchema,
                    toJson(Map.of("expiresHours", 1))
            );

            log.info("Token de reset gerado com sucesso | email={} | accountId={} | tenantSchema={}",
                    normalizedEmail, account.id(), tenantSchema);

            return token;

        } catch (Exception ex) {
            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                    AuditOutcome.FAILURE,
                    null,
                    null,
                    normalizedEmail,
                    null,
                    account.id(),
                    tenantSchema,
                    toJson(Map.of("reason", "error"))
            );

            log.error("Falha gerando token de reset | email={} | accountId={} | tenantSchema={}",
                    normalizedEmail, account.id(), tenantSchema, ex);
            throw ex;
        }
    }

    /**
     * Executa reset de senha a partir de token válido.
     *
     * @param token token de reset
     * @param newPassword nova senha
     */
    public void resetPasswordWithToken(String token, String newPassword) {
        if (!StringUtils.hasText(token)) {
            throw new ApiException(ApiErrorCode.INVALID_TOKEN, "Token inválido", 400);
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória", 400);
        }

        String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(token);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String email = jwtTokenProvider.getEmailFromToken(token);

        securityAuditService.record(
                SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                AuditOutcome.ATTEMPT,
                null,
                null,
                email,
                null,
                accountId,
                tenantSchema,
                toJson(Map.of("stage", "start"))
        );

        try {
            tenantExecutor.runInTenantSchema(tenantSchema, () -> {
                tenantUserCommandService.resetPasswordWithToken(accountId, tenantSchema, email, token, newPassword);
                return null;
            });

            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    AuditOutcome.SUCCESS,
                    null,
                    null,
                    email,
                    null,
                    accountId,
                    tenantSchema,
                    toJson(Map.of("stage", "done"))
            );

            log.info("Reset de senha concluído com sucesso | email={} | accountId={} | tenantSchema={}",
                    email, accountId, tenantSchema);

        } catch (Exception ex) {
            securityAuditService.record(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    AuditOutcome.FAILURE,
                    null,
                    null,
                    email,
                    null,
                    accountId,
                    tenantSchema,
                    toJson(Map.of("reason", "error"))
            );

            log.error("Falha no reset de senha com token | email={} | accountId={} | tenantSchema={}",
                    email, accountId, tenantSchema, ex);
            throw ex;
        }
    }

    /**
     * Serializa details de auditoria de forma centralizada.
     *
     * @param details mapa estruturado
     * @return json serializado
     */
    private String toJson(Map<String, Object> details) {
        return jsonDetailsMapper.toJson(new LinkedHashMap<>(details));
    }
}