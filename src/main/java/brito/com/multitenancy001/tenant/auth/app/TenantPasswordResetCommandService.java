package brito.com.multitenancy001.tenant.auth.app;

import java.time.Duration;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comando responsável pelo fluxo de password reset do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Orquestrar geração de token de reset.</li>
 *   <li>Orquestrar troca de senha por token.</li>
 *   <li>Delegar validações e normalizações ao support.</li>
 *   <li>Delegar auditoria ao audit service.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPasswordResetCommandService {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantUserCommandService tenantUserCommandService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TenantExecutor tenantExecutor;
    private final AppClock appClock;
    private final TenantPasswordResetSupport tenantPasswordResetSupport;
    private final TenantPasswordResetAuditService tenantPasswordResetAuditService;

    /**
     * Gera token de reset de senha para usuário tenant ativo.
     *
     * @param slug slug da account
     * @param email email do usuário
     * @return token de reset
     */
    public String generatePasswordResetToken(String slug, String email) {
        String normalizedSlug = tenantPasswordResetSupport.normalizeSlugOrThrow(slug);
        String normalizedEmail = tenantPasswordResetSupport.normalizeEmailOrThrow(email);

        tenantPasswordResetAuditService.recordPasswordResetRequestedAttempt(normalizedEmail, normalizedSlug);

        AccountSnapshot account = tenantPasswordResetSupport.resolveReadyAccountBySlug(normalizedSlug);
        String tenantSchema = tenantPasswordResetSupport.requireTenantSchema(account);

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

            tenantPasswordResetAuditService.recordPasswordResetRequestedSuccess(
                    normalizedEmail,
                    account.id(),
                    tenantSchema
            );

            log.info("Token de reset gerado com sucesso | email={} | accountId={} | tenantSchema={}",
                    normalizedEmail,
                    account.id(),
                    tenantSchema);

            return token;

        } catch (Exception ex) {
            tenantPasswordResetAuditService.recordPasswordResetRequestedFailure(
                    normalizedEmail,
                    account.id(),
                    tenantSchema,
                    ex
            );

            log.error("Falha gerando token de reset | email={} | accountId={} | tenantSchema={}",
                    normalizedEmail,
                    account.id(),
                    tenantSchema,
                    ex);
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
        String sanitizedToken = tenantPasswordResetSupport.normalizeTokenOrThrow(token);
        String sanitizedPassword = tenantPasswordResetSupport.normalizeNewPasswordOrThrow(newPassword);

        String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(sanitizedToken);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(sanitizedToken);
        String email = jwtTokenProvider.getEmailFromToken(sanitizedToken);

        tenantPasswordResetSupport.assertResetTokenClaims(tenantSchema, accountId, email);

        tenantPasswordResetAuditService.recordPasswordResetCompletedAttempt(
                email,
                accountId,
                tenantSchema
        );

        try {
            tenantExecutor.runInTenantSchema(tenantSchema, () -> {
                tenantUserCommandService.resetPasswordWithToken(
                        accountId,
                        tenantSchema,
                        email,
                        sanitizedToken,
                        sanitizedPassword
                );
                return null;
            });

            tenantPasswordResetAuditService.recordPasswordResetCompletedSuccess(
                    email,
                    accountId,
                    tenantSchema
            );

            log.info("Reset de senha concluído com sucesso | email={} | accountId={} | tenantSchema={}",
                    email,
                    accountId,
                    tenantSchema);

        } catch (Exception ex) {
            tenantPasswordResetAuditService.recordPasswordResetCompletedFailure(
                    email,
                    accountId,
                    tenantSchema,
                    ex
            );

            log.error("Falha no reset de senha com token | email={} | accountId={} | tenantSchema={}",
                    email,
                    accountId,
                    tenantSchema,
                    ex);
            throw ex;
        }
    }
}