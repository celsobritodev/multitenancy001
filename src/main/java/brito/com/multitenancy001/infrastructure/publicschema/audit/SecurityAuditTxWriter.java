package brito.com.multitenancy001.infrastructure.publicschema.audit;

import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.persistence.transaction.PublicTransactionTemplateProvider;
import brito.com.multitenancy001.infrastructure.publicschema.audit.entity.PublicSecurityAuditEvent;
import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writer transacional da auditoria de segurança no schema público.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Persistir eventos de auditoria de segurança em transação pública isolada
 *       ({@code REQUIRES_NEW}).</li>
 *   <li>Normalizar metadados da requisição corrente antes da persistência.</li>
 *   <li>Garantir que {@code detailsJson} nunca seja montado manualmente por concatenação
 *       de string JSON.</li>
 * </ul>
 *
 * <p>Regra importante:</p>
 * <ul>
 *   <li>Quando {@code detailsJson} já vier como JSON válido ({@code {...}} ou {@code [...]}),
 *       ele é preservado.</li>
 *   <li>Quando vier como texto simples, o valor é encapsulado de forma estruturada em
 *       {@code {"raw": ...}} via {@link JsonDetailsMapper}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditTxWriter {

    private final PublicTransactionTemplateProvider publicTx;
    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final AppClock appClock;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Persiste o evento de auditoria em transação pública isolada.
     *
     * @param ev evento solicitado para persistência
     */
    public void write(SecurityAuditRequestedEvent ev) {
        if (ev == null) {
            log.debug("Ignorando write de auditoria de segurança: evento nulo.");
            return;
        }

        log.debug(
                "Persistindo evento de auditoria de segurança. actionType={} outcome={} actorUserId={} targetUserId={} accountId={}",
                ev.actionType(),
                ev.outcome(),
                ev.actorUserId(),
                ev.targetUserId(),
                ev.accountId()
        );

        publicTx.inPublicRequiresNew(() -> doWrite(ev));
    }

    /**
     * Executa a persistência efetiva do evento.
     *
     * @param ev evento solicitado
     */
    private void doWrite(SecurityAuditRequestedEvent ev) {
        Instant occurredAt = (ev.occurredAt() != null) ? ev.occurredAt() : appClock.instant();

        RequestMeta meta = RequestMetaContext.getOrNull();
        UUID requestId = meta != null ? meta.requestId() : null;
        InetAddress ip = meta != null ? parseInetAddressOrNull(meta.ip()) : null;
        String userAgent = meta != null ? meta.userAgent() : null;
        String method = meta != null ? meta.method() : null;
        String uri = meta != null ? meta.uri() : null;

        PublicSecurityAuditEvent entity = new PublicSecurityAuditEvent();
        entity.setOccurredAt(occurredAt);

        entity.setRequestId(requestId);
        entity.setMethod(trimOrNull(method));
        entity.setUri(trimOrNull(uri));
        entity.setIp(ip);
        entity.setUserAgent(trimOrNull(userAgent));

        entity.setActionType(ev.actionType());
        entity.setOutcome(ev.outcome());

        entity.setActorEmail(normalizeEmailOrNull(ev.actorEmail()));
        entity.setActorUserId(ev.actorUserId());

        entity.setTargetEmail(normalizeEmailOrNull(ev.targetEmail()));
        entity.setTargetUserId(ev.targetUserId());

        entity.setAccountId(ev.accountId());
        entity.setTenantSchema(trimOrNull(ev.tenantSchema()));

        entity.setDetailsJson(normalizeDetailsJson(ev.detailsJson()));

        securityAuditEventRepository.save(entity);

        log.debug(
                "Evento de auditoria de segurança persistido com sucesso. actionType={} outcome={} requestId={}",
                ev.actionType(),
                ev.outcome(),
                requestId
        );
    }

    /**
     * Converte string de IP para {@link InetAddress}, retornando {@code null} quando inválido.
     *
     * @param ip texto do IP
     * @return endereço convertido ou {@code null}
     */
    private static InetAddress parseInetAddressOrNull(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }

        try {
            return InetAddress.getByName(ip.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Faz trim e converte texto vazio em {@code null}.
     *
     * @param s valor de entrada
     * @return valor normalizado
     */
    private static String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }

        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Normaliza e-mail para lowercase, retornando {@code null} quando ausente.
     *
     * @param email e-mail de entrada
     * @return e-mail normalizado ou {@code null}
     */
    private static String normalizeEmailOrNull(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }

        String normalized = email.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Normaliza o campo {@code detailsJson}.
     *
     * <p>Regras:</p>
     * <ul>
     *   <li>{@code null}, vazio ou branco => {@code null}</li>
     *   <li>JSON aparente ({@code \{}...} ou {@code [...]}) => preserva</li>
     *   <li>texto simples => encapsula em {@code {"raw": "<texto>"}} usando
     *       {@link JsonDetailsMapper}</li>
     * </ul>
     *
     * @param json conteúdo recebido
     * @return JSON normalizado para persistência
     */
    private String normalizeDetailsJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }

        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("raw", trimmed);

        String normalized = jsonDetailsMapper.toJsonNode(details).toString();

        log.debug("detailsJson textual foi encapsulado como JSON estruturado para auditoria.");
        return normalized;
    }
}