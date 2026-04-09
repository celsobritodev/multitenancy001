package brito.com.multitenancy001.controlplane.accounts.app.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountProvisioningEvent;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountProvisioningEventRepository;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por registrar eventos de auditoria do provisioning de contas
 * no schema público.
 *
 * <p>Objetivos deste serviço:</p>
 * <ul>
 *   <li>Persistir eventos STARTED, SUCCESS e FAILED do fluxo de onboarding/provisioning.</li>
 *   <li>Garantir transação de escrita no public schema.</li>
 *   <li>Normalizar {@code detailsJson} para evitar persistência de conteúdo textual
 *       solto fora de um JSON válido.</li>
 *   <li>Evitar retorno ao anti-pattern de montagem manual de JSON por concatenação.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningAuditService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountProvisioningEventRepository accountProvisioningEventRepository;
    private final AppClock appClock;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Registra início do provisioning.
     *
     * @param accountId identificador da conta
     * @param message mensagem de auditoria
     * @param detailsJson detalhes adicionais em JSON ou texto simples
     */
    public void started(Long accountId, String message, String detailsJson) {
        record(accountId, ProvisioningStatus.STARTED, null, message, detailsJson);
    }

    /**
     * Registra sucesso do provisioning.
     *
     * @param accountId identificador da conta
     * @param message mensagem de auditoria
     * @param detailsJson detalhes adicionais em JSON ou texto simples
     */
    public void success(Long accountId, String message, String detailsJson) {
        record(accountId, ProvisioningStatus.SUCCESS, null, message, detailsJson);
    }

    /**
     * Registra falha do provisioning.
     *
     * @param accountId identificador da conta
     * @param failureCode código de falha funcional/técnica
     * @param message mensagem de auditoria
     * @param detailsJson detalhes adicionais em JSON ou texto simples
     */
    public void failed(Long accountId, ProvisioningFailureCode failureCode, String message, String detailsJson) {
        record(accountId, ProvisioningStatus.FAILED, failureCode, message, detailsJson);
    }

    /**
     * Persiste o evento de auditoria do provisioning.
     *
     * @param accountId identificador da conta
     * @param status status do provisioning
     * @param failureCode código de falha, quando aplicável
     * @param message mensagem de auditoria
     * @param detailsJson detalhes adicionais normalizados antes da persistência
     */
    private void record(
            Long accountId,
            ProvisioningStatus status,
            ProvisioningFailureCode failureCode,
            String message,
            String detailsJson
    ) {
        final String normalizedMessage = trimOrNull(message);
        final String normalizedDetailsJson = normalizeDetailsJson(detailsJson);

        log.info(
                "Registrando auditoria de provisioning. accountId={} status={} failureCode={} message={}",
                accountId,
                status,
                failureCode,
                normalizedMessage
        );

        publicSchemaUnitOfWork.tx(() -> {
            AccountProvisioningEvent event = new AccountProvisioningEvent(
                    accountId,
                    status,
                    failureCode,
                    normalizedMessage,
                    normalizedDetailsJson,
                    appClock.instant()
            );

            accountProvisioningEventRepository.save(event);

            log.debug(
                    "Auditoria de provisioning persistida com sucesso. accountId={} status={}",
                    accountId,
                    status
            );
        });
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

        log.warn("detailsJson recebido como texto simples; encapsulando em JSON estruturado.");

        return jsonDetailsMapper.toJson(details);
    }

    /**
     * Faz trim do texto e converte branco para {@code null}.
     *
     * @param value texto de entrada
     * @return texto normalizado
     */
    private String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}