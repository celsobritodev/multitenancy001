package brito.com.multitenancy001.tenant.auth.app;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pela auditoria do fluxo CONFIRM do login tenant.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Não monta JSON manualmente.</li>
 *   <li>Details nascem estruturados em {@code Map<String, Object>}.</li>
 *   <li>Serialização fica centralizada neste audit service.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantLoginConfirmAuditService {

    private final TenantAuthAuditRecorder tenantAuthAuditRecorder;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Registra tentativa de confirmação do challenge.
     *
     * @param email email autenticado no INIT
     * @param challengeId identificador do challenge
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
                toJson(m(
                        "challengeId", challengeId != null ? challengeId.toString() : null
                ))
        );

        log.debug("Auditoria CONFIRM attempt registrada | email={} | challengeId={}", email, challengeId);
    }

    /**
     * Registra falha do fluxo de confirmação.
     *
     * @param email email autenticado no INIT
     * @param accountId id da conta, quando já conhecido
     * @param tenantSchema schema tenant, quando já conhecido
     * @param reason motivo lógico/técnico da falha
     */
    public void recordFailure(String email, Long accountId, String tenantSchema, String reason) {
        tenantAuthAuditRecorder.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_FAILURE,
                AuditOutcome.FAILURE,
                email,
                null,
                accountId,
                tenantSchema,
                toJson(m("reason", reason))
        );

        log.debug("Auditoria CONFIRM failure registrada | email={} | accountId={} | tenantSchema={} | reason={}",
                email, accountId, tenantSchema, reason);
    }

    /**
     * Registra sucesso do login confirmado.
     *
     * @param email email autenticado no INIT
     * @param userId id do usuário autenticado
     * @param accountId id da conta selecionada
     * @param tenantSchema schema tenant selecionado
     */
    public void recordSuccess(String email, Long userId, Long accountId, String tenantSchema) {
        tenantAuthAuditRecorder.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_SUCCESS,
                AuditOutcome.SUCCESS,
                email,
                userId,
                accountId,
                tenantSchema,
                toJson(m("mode", "challenge_confirm"))
        );

        log.debug("Auditoria CONFIRM success registrada | email={} | userId={} | accountId={} | tenantSchema={}",
                email, userId, accountId, tenantSchema);
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
     * @param details mapa estruturado
     * @return json serializado
     */
    private String toJson(Map<String, Object> details) {
        return details == null ? null : jsonDetailsMapper.toJsonNode(details).toString();
    }
}