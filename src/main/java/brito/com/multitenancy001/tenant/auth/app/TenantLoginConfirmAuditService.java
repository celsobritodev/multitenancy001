package brito.com.multitenancy001.tenant.auth.app;

import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pela auditoria do fluxo de confirmação de login de tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Registrar tentativa de confirmação.</li>
 *   <li>Registrar falhas do fluxo.</li>
 *   <li>Registrar sucesso da emissão de token.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TenantLoginConfirmAuditService {

    private final TenantAuthAuditRecorder tenantAuthAuditRecorder;

    /**
     * Registra tentativa de confirmação de login.
     *
     * @param email email autenticado no INIT
     * @param challengeId challenge utilizado
     */
    public void recordAttempt(String email, UUID challengeId) {
        tenantAuthAuditRecorder.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_CONFIRM,
                AuditOutcome.ATTEMPT,
                email,
                null,
                null,
                null,
                "{\"challengeId\":\"" + challengeId + "\"}"
        );
    }

    /**
     * Registra falha de confirmação de login.
     *
     * @param email email autenticado no INIT
     * @param accountId conta alvo quando disponível
     * @param tenantSchema schema tenant quando disponível
     * @param detailsJson detalhes JSON da falha
     */
    public void recordFailure(
            String email,
            Long accountId,
            String tenantSchema,
            String detailsJson
    ) {
        tenantAuthAuditRecorder.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_CONFIRM,
                AuditOutcome.FAILURE,
                email,
                null,
                accountId,
                tenantSchema,
                detailsJson
        );
    }

    /**
     * Registra sucesso do login confirmado.
     *
     * @param email email autenticado no INIT
     * @param userId id do usuário autenticado
     * @param accountId id da conta selecionada
     * @param tenantSchema schema tenant selecionado
     */
    public void recordSuccess(
            String email,
            Long userId,
            Long accountId,
            String tenantSchema
    ) {
        tenantAuthAuditRecorder.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_SUCCESS,
                AuditOutcome.SUCCESS,
                email,
                userId,
                accountId,
                tenantSchema,
                "{\"mode\":\"challenge_confirm\"}"
        );
    }
}