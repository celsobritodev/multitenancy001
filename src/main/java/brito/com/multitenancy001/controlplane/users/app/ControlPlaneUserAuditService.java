package brito.com.multitenancy001.controlplane.users.app;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pela auditoria do módulo de usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver ator atual de auditoria.</li>
 *   <li>Executar wrapper ATTEMPT/SUCCESS/FAILURE em torno de blocos.</li>
 *   <li>Serializar details via {@link JsonDetailsMapper}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneUserAuditService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneRequestIdentityService controlPlaneRequestIdentityService;
    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Resolve ator atual de auditoria ou devolve ator anônimo.
     *
     * @return ator atual
     */
    public ControlPlaneUserSupport.AuditActor resolveActorOrAnonymous() {
        try {
            Long actorId = controlPlaneRequestIdentityService.getCurrentUserId();
            Long accountId = controlPlaneRequestIdentityService.getCurrentAccountId();

            if (actorId == null || accountId == null) {
                return ControlPlaneUserSupport.AuditActor.anonymous();
            }

            return publicSchemaUnitOfWork.readOnly(() ->
                    controlPlaneUserRepository.findById(actorId)
                            .map(user -> new ControlPlaneUserSupport.AuditActor(actorId, user.getEmail()))
                            .orElse(new ControlPlaneUserSupport.AuditActor(actorId, null))
            );
        } catch (Exception ignored) {
            return ControlPlaneUserSupport.AuditActor.anonymous();
        }
    }

    /**
     * Executa auditoria padrão ATTEMPT/SUCCESS/FAILURE em torno de um bloco.
     *
     * @param actionType tipo de ação
     * @param actor ator executor
     * @param target alvo
     * @param accountId id da conta
     * @param tenantSchema schema tenant quando aplicável
     * @param attemptDetails detalhes de tentativa
     * @param successDetailsSupplier supplier opcional de detalhes de sucesso
     * @param block bloco a executar
     * @param <T> tipo de retorno
     * @return resultado do bloco
     */
    public <T> T auditAttemptSuccessFail(
            SecurityAuditActionType actionType,
            ControlPlaneUserSupport.AuditActor actor,
            ControlPlaneUserSupport.AuditTarget target,
            Long accountId,
            String tenantSchema,
            Map<String, Object> attemptDetails,
            Supplier<Object> successDetailsSupplier,
            ControlPlaneUserSupport.AuditCallable<T> block
    ) {
        recordAudit(
                actionType,
                AuditOutcome.ATTEMPT,
                actor,
                target.email(),
                target.userId(),
                accountId,
                tenantSchema,
                attemptDetails
        );

        try {
            T result = block.call();

            Object successDetails;
            try {
                successDetails = (successDetailsSupplier == null)
                        ? attemptDetails
                        : successDetailsSupplier.get();
            } catch (Exception ignored) {
                successDetails = attemptDetails;
            }

            if (successDetails == null) {
                successDetails = attemptDetails;
            }

            recordAudit(
                    actionType,
                    AuditOutcome.SUCCESS,
                    actor,
                    target.email(),
                    target.userId(),
                    accountId,
                    tenantSchema,
                    successDetails
            );

            return result;
        } catch (ApiException ex) {
            recordAudit(
                    actionType,
                    outcomeFrom(ex),
                    actor,
                    target.email(),
                    target.userId(),
                    accountId,
                    tenantSchema,
                    failureDetails(ControlPlaneUserSupport.SCOPE, ex)
            );
            throw ex;
        } catch (Exception ex) {
            recordAudit(
                    actionType,
                    AuditOutcome.FAILURE,
                    actor,
                    target.email(),
                    target.userId(),
                    accountId,
                    tenantSchema,
                    unexpectedFailureDetails(ControlPlaneUserSupport.SCOPE, ex)
            );
            throw ex;
        }
    }

    /**
     * Registra evento de auditoria.
     *
     * @param actionType ação
     * @param outcome resultado
     * @param actor ator
     * @param targetEmail email do alvo
     * @param targetUserId id do alvo
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     * @param details detalhes livres
     */
    public void recordAudit(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            ControlPlaneUserSupport.AuditActor actor,
            String targetEmail,
            Long targetUserId,
            Long accountId,
            String tenantSchema,
            Object details
    ) {
        securityAuditService.record(
                actionType,
                outcome,
                actor == null ? null : actor.email(),
                actor == null ? null : actor.userId(),
                targetEmail,
                targetUserId,
                accountId,
                tenantSchema,
                toJson(details)
        );
    }

    /**
     * Monta mapa simples a partir de pares chave/valor.
     *
     * @param kv pares chave/valor
     * @return mapa ordenado
     */
    public Map<String, Object> m(Object... kv) {
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
     * Converte detalhes livres para JSON.
     *
     * @param details detalhes
     * @return json serializado ou null
     */
    private String toJson(Object details) {
        if (details == null) {
            return null;
        }

        JsonNode node = jsonDetailsMapper.toJsonNode(details);
        if (node == null || node.isNull()) {
            return null;
        }

        return node.toString();
    }

    /**
     * Resolve outcome de auditoria a partir de ApiException.
     *
     * @param ex exceção de API
     * @return outcome correspondente
     */
    private static AuditOutcome outcomeFrom(ApiException ex) {
        if (ex == null) {
            return AuditOutcome.FAILURE;
        }

        int status = ex.getStatus();
        return (status == 401 || status == 403)
                ? AuditOutcome.DENIED
                : AuditOutcome.FAILURE;
    }

    /**
     * Monta detalhes de falha de negócio.
     *
     * @param scope escopo
     * @param ex exceção
     * @return detalhes
     */
    private static Map<String, Object> failureDetails(String scope, ApiException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", scope);
        details.put("error", ex == null ? null : ex.getError());
        details.put("status", ex == null ? 0 : ex.getStatus());
        details.put("message", safeMessage(ex == null ? null : ex.getMessage()));
        return details;
    }

    /**
     * Monta detalhes de falha inesperada.
     *
     * @param scope escopo
     * @param ex exceção
     * @return detalhes
     */
    private static Map<String, Object> unexpectedFailureDetails(String scope, Exception ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", scope);
        details.put("unexpected", ex == null ? null : ex.getClass().getSimpleName());
        details.put("message", safeMessage(ex == null ? null : ex.getMessage()));
        return details;
    }

    /**
     * Higieniza mensagem livre para auditoria.
     *
     * @param message mensagem
     * @return mensagem tratada
     */
    private static String safeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }

        return message
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .trim();
    }
}