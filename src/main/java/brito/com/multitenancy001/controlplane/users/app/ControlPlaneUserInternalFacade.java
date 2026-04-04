package brito.com.multitenancy001.controlplane.users.app;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import lombok.RequiredArgsConstructor;

/**
 * Fachada fina de apoio para o módulo de usuários do Control Plane.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar o contrato atual usado pelos call-sites existentes.</li>
 *   <li>Delegar responsabilidades reais para componentes menores e semânticos.</li>
 * </ul>
 *
 * <p>Observação importante:</p>
 * <ul>
 *   <li>Esta classe não deve voltar a concentrar regra pesada.</li>
 *   <li>Validação, loading, mapeamento e auditoria ficam em serviços especializados.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ControlPlaneUserInternalFacade {

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

    private final ControlPlaneUserAccountSupport controlPlaneUserAccountSupport;
    private final ControlPlaneUserValidationService controlPlaneUserValidationService;
    private final ControlPlaneUserLoader controlPlaneUserLoader;
    private final ControlPlaneUserResponseMapper controlPlaneUserResponseMapper;
    private final ControlPlaneUserAuditService controlPlaneUserAuditService;

    /**
     * Obtém a conta única do Control Plane.
     *
     * @return conta do Control Plane
     */
    public Account getControlPlaneAccount() {
        return controlPlaneUserAccountSupport.getControlPlaneAccount();
    }

    /**
     * Normaliza e valida email obrigatório.
     *
     * @param raw valor bruto
     * @return email normalizado
     */
    public String normalizeEmailOrThrow(String raw) {
        return controlPlaneUserValidationService.normalizeEmailOrThrow(raw);
    }

    /**
     * Normaliza e valida nome obrigatório.
     *
     * @param raw valor bruto
     * @return nome normalizado
     */
    public String normalizeNameOrThrow(String raw) {
        return controlPlaneUserValidationService.normalizeNameOrThrow(raw);
    }

    /**
     * Valida se o email não é reservado para usuário built-in.
     *
     * @param normalizedEmail email já normalizado
     */
    public void validateNotReservedEmail(String normalizedEmail) {
        controlPlaneUserValidationService.validateNotReservedEmail(normalizedEmail);
    }

    /**
     * Carrega usuário não deletado no escopo do Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneAccountId id da conta do Control Plane
     * @return usuário encontrado
     */
    public ControlPlaneUser loadNotDeletedUserInControlPlane(Long userId, Long controlPlaneAccountId) {
        return controlPlaneUserLoader.loadNotDeletedUserInControlPlane(userId, controlPlaneAccountId);
    }

    /**
     * Carrega usuário por id e valida escopo no Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneAccountId id da conta do Control Plane
     * @return usuário encontrado
     */
    public ControlPlaneUser loadUserInControlPlane(Long userId, Long controlPlaneAccountId) {
        return controlPlaneUserLoader.loadUserInControlPlane(userId, controlPlaneAccountId);
    }

    /**
     * Carrega usuário habilitado por id no escopo do Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneAccountId id da conta do Control Plane
     * @return usuário habilitado
     */
    public ControlPlaneUser loadEnabledUserInControlPlane(Long userId, Long controlPlaneAccountId) {
        return controlPlaneUserLoader.loadEnabledUserInControlPlane(userId, controlPlaneAccountId);
    }

    /**
     * Garante que o usuário não seja built-in imutável.
     *
     * @param user usuário alvo
     */
    public void assertMutableUser(ControlPlaneUser user) {
        controlPlaneUserValidationService.assertMutableUser(user);
    }

    /**
     * Mapeia usuário do domínio para DTO de detalhes.
     *
     * @param user usuário do domínio
     * @return DTO de detalhes
     */
    public ControlPlaneUserDetailsResponse mapToDetailsResponse(ControlPlaneUser user) {
        return controlPlaneUserResponseMapper.mapToDetailsResponse(user);
    }

    /**
     * Mapeia usuário do domínio para DTO do endpoint /me.
     *
     * @param user usuário autenticado
     * @return DTO /me
     */
    public ControlPlaneMeResponse mapToMeResponse(ControlPlaneUser user) {
        return controlPlaneUserResponseMapper.mapToMeResponse(user);
    }

    /**
     * Resolve ator atual de auditoria ou devolve ator anônimo.
     *
     * @return ator atual
     */
    public AuditActor resolveActorOrAnonymous() {
        return controlPlaneUserAuditService.resolveActorOrAnonymous();
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
            AuditActor actor,
            AuditTarget target,
            Long accountId,
            String tenantSchema,
            Map<String, Object> attemptDetails,
            Supplier<Object> successDetailsSupplier,
            AuditCallable<T> block
    ) {
        return controlPlaneUserAuditService.auditAttemptSuccessFail(
                actionType,
                actor,
                target,
                accountId,
                tenantSchema,
                attemptDetails,
                successDetailsSupplier,
                block
        );
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
        controlPlaneUserAuditService.recordAudit(
                actionType,
                outcome,
                actor,
                targetEmail,
                targetUserId,
                accountId,
                tenantSchema,
                details
        );
    }

    /**
     * Monta mapa simples a partir de pares chave/valor.
     *
     * @param kv pares chave/valor
     * @return mapa ordenado
     */
    public Map<String, Object> m(Object... kv) {
        return controlPlaneUserAuditService.m(kv);
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