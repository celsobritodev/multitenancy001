package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.Map;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pela auditoria estruturada de mudanças de status e lifecycle de Account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Converter details estruturados em JSON.</li>
 *   <li>Persistir eventos append-only no public schema.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AccountStatusAuditService {

    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Registra evento de auditoria append-only.
     *
     * @param actionType tipo da ação
     * @param outcome desfecho
     * @param actorEmail email do ator
     * @param actorUserId id do ator
     * @param accountId id da conta alvo
     * @param tenantSchema schema tenant quando aplicável
     * @param details detalhes estruturados
     */
    public void recordAudit(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            String actorEmail,
            Long actorUserId,
            Long accountId,
            String tenantSchema,
            Map<String, Object> details
    ) {
        String detailsJson = details == null
                ? null
                : jsonDetailsMapper.toJsonNode(details).toString();

        securityAuditService.record(
                actionType,
                outcome,
                actorEmail,
                actorUserId,
                null,
                null,
                accountId,
                tenantSchema,
                detailsJson
        );
    }
}