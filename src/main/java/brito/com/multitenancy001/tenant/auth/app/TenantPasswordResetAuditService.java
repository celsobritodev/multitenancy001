package brito.com.multitenancy001.tenant.auth.app;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pela auditoria do fluxo de password reset do tenant.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Details nascem estruturados em {@code Map<String, Object>}.</li>
 *   <li>Serialização JSON fica centralizada neste service.</li>
 *   <li>Nenhum call-site deve montar JSON manualmente.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TenantPasswordResetAuditService {

    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Registra tentativa de geração de token de reset.
     *
     * @param email email alvo
     * @param slug slug informado
     */
    public void recordPasswordResetRequestedAttempt(String email, String slug) {
        recordAudit(
                SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                AuditOutcome.ATTEMPT,
                email,
                null,
                null,
                m("slug", slug)
        );
    }

    /**
     * Registra sucesso da geração de token de reset.
     *
     * @param email email alvo
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     */
    public void recordPasswordResetRequestedSuccess(
            String email,
            Long accountId,
            String tenantSchema
    ) {
        recordAudit(
                SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                AuditOutcome.SUCCESS,
                email,
                accountId,
                tenantSchema,
                m("expiresHours", 1)
        );
    }

    /**
     * Registra falha da geração de token de reset.
     *
     * @param email email alvo
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     * @param cause causa da falha
     */
    public void recordPasswordResetRequestedFailure(
            String email,
            Long accountId,
            String tenantSchema,
            Throwable cause
    ) {
        recordAudit(
                SecurityAuditActionType.PASSWORD_RESET_REQUESTED,
                AuditOutcome.FAILURE,
                email,
                accountId,
                tenantSchema,
                failureDetails(cause)
        );
    }

    /**
     * Registra tentativa do reset concluído por token.
     *
     * @param email email alvo
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     */
    public void recordPasswordResetCompletedAttempt(
            String email,
            Long accountId,
            String tenantSchema
    ) {
        recordAudit(
                SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                AuditOutcome.ATTEMPT,
                email,
                accountId,
                tenantSchema,
                m("stage", "start")
        );
    }

    /**
     * Registra sucesso do reset concluído por token.
     *
     * @param email email alvo
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     */
    public void recordPasswordResetCompletedSuccess(
            String email,
            Long accountId,
            String tenantSchema
    ) {
        recordAudit(
                SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                AuditOutcome.SUCCESS,
                email,
                accountId,
                tenantSchema,
                m("stage", "done")
        );
    }

    /**
     * Registra falha do reset concluído por token.
     *
     * @param email email alvo
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     * @param cause causa da falha
     */
    public void recordPasswordResetCompletedFailure(
            String email,
            Long accountId,
            String tenantSchema,
            Throwable cause
    ) {
        recordAudit(
                SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                AuditOutcome.FAILURE,
                email,
                accountId,
                tenantSchema,
                failureDetails(cause)
        );
    }

    /**
     * Registra evento de auditoria append-only.
     *
     * @param actionType tipo da ação
     * @param outcome desfecho
     * @param targetEmail email alvo
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     * @param details detalhes estruturados
     */
    private void recordAudit(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            String targetEmail,
            Long accountId,
            String tenantSchema,
            Map<String, Object> details
    ) {
        securityAuditService.record(
                actionType,
                outcome,
                null,
                null,
                targetEmail,
                null,
                accountId,
                tenantSchema,
                toJson(details)
        );
    }

    /**
     * Monta details padronizados de falha.
     *
     * @param cause causa da falha
     * @return mapa estruturado
     */
    private Map<String, Object> failureDetails(Throwable cause) {
        return m(
                "reason", "error",
                "causeClass", cause == null ? null : cause.getClass().getName(),
                "causeMessage", cause == null ? null : safeMessage(cause)
        );
    }

    /**
     * Retorna mensagem segura da causa.
     *
     * @param cause causa original
     * @return mensagem segura
     */
    private String safeMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
            return cause == null ? "unknown" : cause.getClass().getSimpleName();
        }
        return cause.getMessage().trim();
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
     * Serializa details de auditoria.
     *
     * @param details mapa estruturado
     * @return json serializado
     */
    private String toJson(Map<String, Object> details) {
        return details == null ? null : jsonDetailsMapper.toJsonNode(details).toString();
    }
}