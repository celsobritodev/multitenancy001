package brito.com.multitenancy001.controlplane.users.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneChangeMyPasswordRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPasswordResetRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserSuspendRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada principal de usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Manter compatibilidade com os controllers atuais.</li>
 *   <li>Expor uma superfície pública estável para os casos de uso de usuários.</li>
 *   <li>Delegar para serviços especializados por responsabilidade.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Esta classe deve permanecer fina.</li>
 *   <li>Não deve concentrar regra de negócio, auditoria, lifecycle,
 *       sincronização de identidade ou fluxo de senha.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private final ControlPlaneUserCommandService controlPlaneUserCommandService;
    private final ControlPlaneUserQueryService controlPlaneUserQueryService;
    private final ControlPlaneUserPasswordService controlPlaneUserPasswordService;
    private final ControlPlaneUserLifecycleService controlPlaneUserLifecycleService;

    /**
     * Cria usuário do Control Plane.
     *
     * @param controlPlaneUserCreateRequest request de criação
     * @return usuário criado
     */
    public ControlPlaneUserDetailsResponse createControlPlaneUser(
            ControlPlaneUserCreateRequest controlPlaneUserCreateRequest
    ) {
        log.info("Delegando createControlPlaneUser para command service.");
        return controlPlaneUserCommandService.createControlPlaneUser(controlPlaneUserCreateRequest);
    }

    /**
     * Atualiza usuário do Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneUserUpdateRequest request de atualização
     * @return usuário atualizado
     */
    public ControlPlaneUserDetailsResponse updateControlPlaneUser(
            Long userId,
            ControlPlaneUserUpdateRequest controlPlaneUserUpdateRequest
    ) {
        log.info("Delegando updateControlPlaneUser para command service. userId={}", userId);
        return controlPlaneUserCommandService.updateControlPlaneUser(userId, controlPlaneUserUpdateRequest);
    }

    /**
     * Atualiza permissões explícitas de usuário do Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneUserPermissionsUpdateRequest request de permissões
     * @return usuário atualizado
     */
    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(
            Long userId,
            ControlPlaneUserPermissionsUpdateRequest controlPlaneUserPermissionsUpdateRequest
    ) {
        log.info("Delegando updateControlPlaneUserPermissions para command service. userId={}", userId);
        return controlPlaneUserCommandService.updateControlPlaneUserPermissions(
                userId,
                controlPlaneUserPermissionsUpdateRequest
        );
    }

    /**
     * Lista usuários do Control Plane.
     *
     * @return lista de usuários
     */
    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        return controlPlaneUserQueryService.listControlPlaneUsers();
    }

    /**
     * Obtém usuário do Control Plane por id.
     *
     * @param userId id do usuário
     * @return usuário encontrado
     */
    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        return controlPlaneUserQueryService.getControlPlaneUser(userId);
    }

    /**
     * Lista usuários habilitados do Control Plane.
     *
     * @return lista de usuários habilitados
     */
    public List<ControlPlaneUserDetailsResponse> listEnabledControlPlaneUsers() {
        return controlPlaneUserQueryService.listEnabledControlPlaneUsers();
    }

    /**
     * Obtém usuário habilitado do Control Plane por id.
     *
     * @param userId id do usuário
     * @return usuário habilitado
     */
    public ControlPlaneUserDetailsResponse getEnabledControlPlaneUser(Long userId) {
        return controlPlaneUserQueryService.getEnabledControlPlaneUser(userId);
    }

    /**
     * Obtém dados do usuário autenticado do Control Plane.
     *
     * @return dados do usuário autenticado
     */
    public ControlPlaneMeResponse getMe() {
        return controlPlaneUserQueryService.getMe();
    }

    /**
     * Reseta senha de usuário do Control Plane por administrador.
     *
     * @param userId id do usuário alvo
     * @param controlPlaneUserPasswordResetRequest request de reset
     */
    public void resetControlPlaneUserPassword(
            Long userId,
            ControlPlaneUserPasswordResetRequest controlPlaneUserPasswordResetRequest
    ) {
        log.info("Delegando resetControlPlaneUserPassword para password service. userId={}", userId);
        controlPlaneUserPasswordService.resetControlPlaneUserPassword(userId, controlPlaneUserPasswordResetRequest);
    }

    /**
     * Altera a própria senha do usuário autenticado.
     *
     * @param controlPlaneChangeMyPasswordRequest request de troca de senha
     */
    public void changeMyPassword(ControlPlaneChangeMyPasswordRequest controlPlaneChangeMyPasswordRequest) {
        log.info("Delegando changeMyPassword para password service.");
        controlPlaneUserPasswordService.changeMyPassword(controlPlaneChangeMyPasswordRequest);
    }

    /**
     * Realiza soft delete de usuário do Control Plane.
     *
     * @param userId id do usuário
     */
    public void softDeleteControlPlaneUser(Long userId) {
        log.info("Delegando softDeleteControlPlaneUser para lifecycle service. userId={}", userId);
        controlPlaneUserLifecycleService.softDeleteControlPlaneUser(userId);
    }

    /**
     * Restaura usuário soft-deleted do Control Plane.
     *
     * @param userId id do usuário
     * @return usuário restaurado
     */
    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        log.info("Delegando restoreControlPlaneUser para lifecycle service. userId={}", userId);
        return controlPlaneUserLifecycleService.restoreControlPlaneUser(userId);
    }

    /**
     * Suspende usuário por ação administrativa.
     *
     * @param userId id do usuário
     * @param controlPlaneUserSuspendRequest request opcional de suspensão
     */
    public void suspendControlPlaneUserByAdmin(
            Long userId,
            ControlPlaneUserSuspendRequest controlPlaneUserSuspendRequest
    ) {
        log.info("Delegando suspendControlPlaneUserByAdmin para lifecycle service. userId={}", userId);
        controlPlaneUserLifecycleService.suspendControlPlaneUserByAdmin(userId, controlPlaneUserSuspendRequest);
    }

    /**
     * Remove suspensão administrativa do usuário.
     *
     * @param userId id do usuário
     * @param controlPlaneUserSuspendRequest request opcional de restauração
     */
    public void restoreControlPlaneUserByAdmin(
            Long userId,
            ControlPlaneUserSuspendRequest controlPlaneUserSuspendRequest
    ) {
        log.info("Delegando restoreControlPlaneUserByAdmin para lifecycle service. userId={}", userId);
        controlPlaneUserLifecycleService.restoreControlPlaneUserByAdmin(userId, controlPlaneUserSuspendRequest);
    }
}