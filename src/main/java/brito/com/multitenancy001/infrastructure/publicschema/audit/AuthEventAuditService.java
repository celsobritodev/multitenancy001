package brito.com.multitenancy001.infrastructure.publicschema.audit;

import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por persistir eventos de auditoria de autenticação
 * no schema público.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Registrar eventos de autenticação com transação isolada no public schema.</li>
 *   <li>Capturar metadados da request corrente quando disponíveis.</li>
 *   <li>Normalizar {@code detailsJson} para evitar persistência de texto cru fora
 *       de um JSON válido.</li>
 *   <li>Impedir regressão para montagem manual de JSON por concatenação.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthEventAuditService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AuthEventRepository authEventRepository;
    private final AppClock appClock;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Registra um evento de auditoria de autenticação.
     *
     * @param authDomain domínio funcional da autenticação
     * @param eventType tipo do evento
     * @param outcome resultado da operação
     * @param principalEmail email principal envolvido
     * @param principalUserId id do usuário principal
     * @param accountId id da conta
     * @param tenantSchema schema tenant informado explicitamente
     * @param detailsJson detalhes do evento em JSON ou texto simples
     */
    public void record(
            AuthDomain authDomain,
            AuthEventType eventType,
            AuditOutcome outcome,
            String principalEmail,
            Long principalUserId,
            Long accountId,
            String tenantSchema,
            String detailsJson
    ) {

        RequestMeta meta = RequestMetaContext.getOrNull();
        String resolvedTenant = resolveTenantSchema(tenantSchema);
        String normalizedPrincipalEmail = normalizeEmailOrNull(principalEmail);
        String normalizedDetailsJson = normalizeDetailsJson(detailsJson);

        log.info(
                "Registrando auth audit. domain={} eventType={} outcome={} principalUserId={} accountId={} tenantSchema={}",
                authDomain,
                eventType,
                outcome,
                principalUserId,
                accountId,
                resolvedTenant
        );

        publicSchemaUnitOfWork.requiresNew(() -> {
            AuthEvent ev = new AuthEvent();

            Instant occurredAt = appClock.instant();
            ev.setOccurredAt(occurredAt);

            if (meta != null) {
                ev.setRequestId(meta.requestId());
                ev.setMethod(trimOrNull(meta.method()));
                ev.setUri(trimOrNull(meta.uri()));
                ev.setIp(parseInetOrNull(meta.ip()));
                ev.setUserAgent(trimOrNull(meta.userAgent()));
            }

            ev.setAuthDomain(authDomain);
            ev.setEventType(eventType);
            ev.setOutcome(outcome);

            ev.setPrincipalEmail(normalizedPrincipalEmail);
            ev.setPrincipalUserId(principalUserId);

            ev.setAccountId(accountId);
            ev.setTenantSchema(resolvedTenant);

            ev.setDetailsJson(normalizedDetailsJson);

            authEventRepository.save(ev);

            log.debug(
                    "Auth audit persistido com sucesso. domain={} eventType={} outcome={} accountId={}",
                    authDomain,
                    eventType,
                    outcome,
                    accountId
            );
        });
    }

    /**
     * Resolve o tenant schema preferindo o valor explicitamente informado e,
     * na ausência dele, usando o {@link TenantContext} corrente.
     *
     * @param tenantSchema schema informado
     * @return schema normalizado ou {@code null}
     */
    private String resolveTenantSchema(String tenantSchema) {
        String explicit = trimOrNull(tenantSchema);
        if (explicit != null) {
            return explicit;
        }
        return trimOrNull(TenantContext.getOrNull());
    }

    /**
     * Normaliza o conteúdo de {@code detailsJson}.
     *
     * <p>Regras:</p>
     * <ul>
     *   <li>{@code null}, vazio ou branco => retorna {@code null}</li>
     *   <li>se já parecer JSON ({@code {...}} ou {@code [...]}) => preserva</li>
     *   <li>se vier texto simples => encapsula como {@code {"raw":"..."}}</li>
     * </ul>
     *
     * @param detailsJson conteúdo original
     * @return JSON normalizado para persistência
     */
    private String normalizeDetailsJson(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return null;
        }

        String trimmed = detailsJson.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("raw", trimmed);

        log.warn("detailsJson de auth recebido como texto simples; encapsulando em JSON estruturado.");

        return jsonDetailsMapper.toJson(details);
    }

    /**
     * Normaliza e-mail para lowercase.
     *
     * @param email email bruto
     * @return email normalizado ou {@code null}
     */
    private String normalizeEmailOrNull(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }

        String trimmed = email.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Faz trim do texto e converte vazio em {@code null}.
     *
     * @param value valor bruto
     * @return valor normalizado
     */
    private String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Converte IP textual para {@link InetAddress}.
     *
     * @param rawIp ip bruto
     * @return endereço convertido ou {@code null}
     */
    private static InetAddress parseInetOrNull(String rawIp) {
        if (!StringUtils.hasText(rawIp)) {
            return null;
        }

        try {
            return InetAddress.getByName(rawIp.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}