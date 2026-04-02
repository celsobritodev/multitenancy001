package brito.com.multitenancy001.controlplane.users.app;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneBuiltInUsers;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente de apoio para o módulo de usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Centralizar helpers compartilhados de account, user, auditoria e mapeamento.</li>
 *   <li>Evitar duplicação entre command, query, lifecycle e password services.</li>
 *   <li>Padronizar validações transversais do módulo.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ControlPlaneUserSupport {

    /**
     * Mensagem padrão para tentativa de alteração de usuário built-in.
     */
    public static final String BUILTIN_IMMUTABLE_MESSAGE =
            "Usuário BUILT_IN é protegido: não pode ser alterado/deletado/restaurado/suspenso; apenas senha pode ser trocada.";

    /**
     * Mensagem padrão para configuração inválida da conta do Control Plane.
     */
    public static final String MSG_CP_ACCOUNT_INVALID =
            "Configuração inválida: conta do Control Plane ausente ou duplicada.";

    /**
     * Escopo padrão de auditoria do módulo.
     */
    public static final String SCOPE = "CONTROL_PLANE";

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneRequestIdentityService controlPlaneRequestIdentityService;
    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Obtém a conta única do Control Plane.
     *
     * @return conta do Control Plane
     */
    public Account getControlPlaneAccount() {
        try {
            return accountRepository.getSingleControlPlaneAccount();
        } catch (IllegalStateException ex) {
            throw new ApiException(
                    ApiErrorCode.CONTROLPLANE_ACCOUNT_INVALID,
                    MSG_CP_ACCOUNT_INVALID + " " + ex.getMessage(),
                    500
            );
        }
    }

    /**
     * Normaliza e valida email obrigatório.
     *
     * @param raw valor bruto
     * @return email normalizado
     */
    public String normalizeEmailOrThrow(String raw) {
        String email = EmailNormalizer.normalizeOrNull(raw);
        if (email == null) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email inválido", 400);
        }
        return email;
    }

    /**
     * Normaliza e valida nome obrigatório.
     *
     * @param raw valor bruto
     * @return nome normalizado
     */
    public String normalizeNameOrThrow(String raw) {
        if (raw == null) {
            throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome é obrigatório", 400);
        }

        String name = raw.trim();
        if (name.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome é obrigatório", 400);
        }

        return name;
    }

    /**
     * Valida se o email não é reservado para usuário built-in.
     *
     * @param normalizedEmail email já normalizado
     */
    public void validateNotReservedEmail(String normalizedEmail) {
        if (ControlPlaneBuiltInUsers.isReservedEmail(normalizedEmail)) {
            throw new ApiException(
                    ApiErrorCode.EMAIL_RESERVED,
                    "Este email é reservado do sistema (BUILT_IN)",
                    409
            );
        }
    }

    /**
     * Carrega usuário não deletado no escopo do Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneAccountId id da conta do Control Plane
     * @return usuário encontrado
     */
    public ControlPlaneUser loadNotDeletedUserInControlPlane(Long userId, Long controlPlaneAccountId) {
        return controlPlaneUserRepository
                .findNotDeletedByIdAndAccountId(userId, controlPlaneAccountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Usuário não encontrado",
                        404
                ));
    }

    /**
     * Carrega usuário por id e valida escopo no Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneAccountId id da conta do Control Plane
     * @return usuário encontrado
     */
    public ControlPlaneUser loadUserInControlPlane(Long userId, Long controlPlaneAccountId) {
        ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Usuário não encontrado",
                        404
                ));

        if (user.getAccount() == null
                || user.getAccount().getId() == null
                || !user.getAccount().getId().equals(controlPlaneAccountId)) {
            throw new ApiException(
                    ApiErrorCode.USER_OUT_OF_SCOPE,
                    "Usuário não pertence ao Control Plane",
                    403
            );
        }

        return user;
    }

    /**
     * Carrega usuário habilitado por id no escopo do Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneAccountId id da conta do Control Plane
     * @return usuário habilitado
     */
    public ControlPlaneUser loadEnabledUserInControlPlane(Long userId, Long controlPlaneAccountId) {
        return controlPlaneUserRepository
                .findEnabledByIdAndAccountId(userId, controlPlaneAccountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.USER_NOT_ENABLED,
                        "Usuário não encontrado ou não habilitado",
                        404
                ));
    }

    /**
     * Garante que o usuário não seja built-in imutável.
     *
     * @param user usuário alvo
     */
    public void assertMutableUser(ControlPlaneUser user) {
        if (user != null && user.isBuiltInUser()) {
            throw new ApiException(
                    ApiErrorCode.USER_BUILT_IN_IMMUTABLE,
                    BUILTIN_IMMUTABLE_MESSAGE,
                    409
            );
        }
    }

    /**
     * Mapeia usuário do domínio para DTO de detalhes.
     *
     * @param user usuário do domínio
     * @return DTO de detalhes
     */
    public ControlPlaneUserDetailsResponse mapToDetailsResponse(ControlPlaneUser user) {
        return new ControlPlaneUserDetailsResponse(
                user.getId(),
                user.getAccount().getId(),
                user.getName(),
                user.getEmail(),
                SystemRoleName.fromString(user.getRole() == null ? null : user.getRole().name()),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.isDeleted(),
                user.isEnabled(),
                user.getAudit() == null ? null : user.getAudit().getCreatedAt()
        );
    }

    /**
     * Mapeia usuário do domínio para DTO do endpoint /me.
     *
     * @param user usuário autenticado
     * @return DTO /me
     */
    public ControlPlaneMeResponse mapToMeResponse(ControlPlaneUser user) {
        return new ControlPlaneMeResponse(
                user.getId(),
                user.getAccount().getId(),
                user.getName(),
                user.getEmail(),
                SystemRoleName.fromString(user.getRole() == null ? null : user.getRole().name()),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.isDeleted(),
                user.isEnabled()
        );
    }

    /**
     * Resolve ator atual de auditoria ou devolve ator anônimo.
     *
     * @return ator atual
     */
    public AuditActor resolveActorOrAnonymous() {
        try {
            Long actorId = controlPlaneRequestIdentityService.getCurrentUserId();
            Long accountId = controlPlaneRequestIdentityService.getCurrentAccountId();

            if (actorId == null || accountId == null) {
                return AuditActor.anonymous();
            }

            return publicSchemaUnitOfWork.readOnly(() ->
                    controlPlaneUserRepository.findById(actorId)
                            .map(user -> new AuditActor(actorId, user.getEmail()))
                            .orElse(new AuditActor(actorId, null))
            );
        } catch (Exception ignored) {
            return AuditActor.anonymous();
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
     * @return resultado do bloco
     * @param <T> tipo de retorno
     */
    public <T> T auditAttemptSuccessFail(
            SecurityAuditActionType actionType,
            AuditActor actor,
            AuditTarget target,
            Long accountId,
            String tenantSchema,
            Map<String, Object> attemptDetails,
            Supplier<Object> successDetailsSupplier,
            AuditCallable<T> block
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
                    failureDetails(SCOPE, ex)
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
                    unexpectedFailureDetails(SCOPE, ex)
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
            AuditActor actor,
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
        return Map.of(
                "scope", scope,
                "error", ex == null ? null : ex.getError(),
                "status", ex == null ? 0 : ex.getStatus(),
                "message", safeMessage(ex == null ? null : ex.getMessage())
        );
    }

    /**
     * Monta detalhes de falha inesperada.
     *
     * @param scope escopo
     * @param ex exceção
     * @return detalhes
     */
    private static Map<String, Object> unexpectedFailureDetails(String scope, Exception ex) {
        return Map.of(
                "scope", scope,
                "unexpected", ex == null ? null : ex.getClass().getSimpleName(),
                "message", safeMessage(ex == null ? null : ex.getMessage())
        );
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

    /**
     * Representa o ator executor de auditoria.
     *
     * @param userId id do ator
     * @param email email do ator
     */
    public record AuditActor(Long userId, String email) {

        /**
         * Cria ator anônimo.
         *
         * @return ator anônimo
         */
        public static AuditActor anonymous() {
            return new AuditActor(null, null);
        }
    }

    /**
     * Representa o alvo da operação auditada.
     *
     * @param email email do alvo
     * @param userId id do alvo
     */
    public record AuditTarget(String email, Long userId) {
    }

    /**
     * Contrato funcional para execução auditada.
     *
     * @param <T> tipo do retorno
     */
    @FunctionalInterface
    public interface AuditCallable<T> {

        /**
         * Executa o bloco.
         *
         * @return resultado
         */
        T call();
    }
}