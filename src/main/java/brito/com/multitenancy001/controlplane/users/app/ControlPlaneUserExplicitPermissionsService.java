package brito.com.multitenancy001.controlplane.users.app;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por definir permissões explícitas (override) para usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Receber códigos de permissões explícitas informados pela camada de aplicação.</li>
 *   <li>Normalizar e validar permissões estritamente no escopo do Control Plane.</li>
 *   <li>Garantir que o usuário alvo exista, esteja ativo (não deleted) e pertença ao Control Plane.</li>
 *   <li>Bloquear alteração de permissões para usuários BUILT_IN.</li>
 *   <li>Persistir o override explícito de permissões em transação no PUBLIC schema.</li>
 * </ul>
 *
 * <p>Regras importantes:</p>
 * <ul>
 *   <li>Apenas códigos {@code CP_*} são aceitos.</li>
 *   <li>Todo código informado deve existir no enum {@link ControlPlanePermission}.</li>
 *   <li>O usuário alvo deve ser do Control Plane.</li>
 *   <li>Usuários BUILT_IN são imutáveis para permissões explícitas.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Este serviço atua apenas sobre override explícito de permissões.</li>
 *   <li>Não deve assumir responsabilidades de controller, mapper ou query pública.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserExplicitPermissionsService {

    private static final String BUILTIN_IMMUTABLE_MESSAGE =
            "Usuário BUILT_IN é protegido: não pode ter permissões alteradas; apenas senha pode ser trocada.";

    private static final String CONTROL_PLANE_ACCOUNT_INVALID_MESSAGE =
            "Configuração inválida: conta do Control Plane ausente ou duplicada.";

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    /**
     * Define permissões explícitas (override) para o usuário informado a partir de códigos textuais.
     *
     * <p>Fluxo:</p>
     * <ul>
     *   <li>Valida {@code userId} obrigatório.</li>
     *   <li>Normaliza e valida os códigos estritamente no escopo do Control Plane.</li>
     *   <li>Converte os códigos normalizados para {@link ControlPlanePermission}.</li>
     *   <li>Abre transação no PUBLIC schema.</li>
     *   <li>Resolve a conta única do Control Plane.</li>
     *   <li>Carrega o usuário não deletado.</li>
     *   <li>Valida escopo do usuário e proteção BUILT_IN.</li>
     *   <li>Substitui integralmente o conjunto de permissões explícitas.</li>
     * </ul>
     *
     * @param userId id do usuário alvo
     * @param permissionCodes coleção de códigos de permissões explícitas
     */
    public void setExplicitPermissionsFromCodes(Long userId, Collection<String> permissionCodes) {
        validateUserIdRequired(userId);

        LinkedHashSet<String> normalizedPermissionCodes =
                PermissionScopeValidator.normalizeControlPlaneStrict(permissionCodes);

        Set<ControlPlanePermission> explicitPermissions =
                mapToControlPlanePermissions(normalizedPermissionCodes);

        log.info(
                "Iniciando atualização de permissões explícitas do usuário do Control Plane. userId={}, permissionCount={}",
                userId,
                explicitPermissions.size()
        );

        publicSchemaUnitOfWork.tx(() -> {
            Account controlPlaneAccount = resolveSingleControlPlaneAccount();
            ControlPlaneUser controlPlaneUser = loadActiveUserRequired(userId);

            validateUserBelongsToControlPlane(controlPlaneUser, controlPlaneAccount);
            validateBuiltInUserImmutable(controlPlaneUser);

            controlPlaneUser.replaceExplicitPermissions(explicitPermissions);
            controlPlaneUserRepository.save(controlPlaneUser);

            log.info(
                    "Permissões explícitas atualizadas com sucesso para usuário do Control Plane. userId={}, permissionCount={}",
                    userId,
                    explicitPermissions.size()
            );

            return null;
        });
    }

    /**
     * Valida {@code userId} obrigatório.
     *
     * @param userId id do usuário
     */
    private void validateUserIdRequired(Long userId) {
        if (userId == null) {
            throw new ApiException(
                    ApiErrorCode.USER_ID_REQUIRED,
                    "userId é obrigatório",
                    400
            );
        }
    }

    /**
     * Converte códigos normalizados para enum {@link ControlPlanePermission}.
     *
     * @param normalizedPermissionCodes códigos já normalizados
     * @return conjunto tipado de permissões explícitas
     */
    private Set<ControlPlanePermission> mapToControlPlanePermissions(
            Collection<String> normalizedPermissionCodes
    ) {
        if (normalizedPermissionCodes == null || normalizedPermissionCodes.isEmpty()) {
            return new LinkedHashSet<>();
        }

        return normalizedPermissionCodes.stream()
                .filter(Objects::nonNull)
                .map(this::toControlPlanePermission)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Converte um código textual para {@link ControlPlanePermission}.
     *
     * @param permissionCode código textual
     * @return enum correspondente
     */
    private ControlPlanePermission toControlPlanePermission(String permissionCode) {
        try {
            return ControlPlanePermission.valueOf(permissionCode);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    ApiErrorCode.INVALID_PERMISSION,
                    "Permission não existe no enum ControlPlanePermission: " + permissionCode,
                    400
            );
        }
    }

    /**
     * Resolve a conta única do Control Plane.
     *
     * @return conta do Control Plane
     */
    private Account resolveSingleControlPlaneAccount() {
        try {
            return accountRepository.getSingleControlPlaneAccount();
        } catch (IllegalStateException ex) {
            throw new ApiException(
                    ApiErrorCode.CONTROLPLANE_ACCOUNT_INVALID,
                    CONTROL_PLANE_ACCOUNT_INVALID_MESSAGE + " " + ex.getMessage(),
                    500
            );
        }
    }

    /**
     * Carrega usuário ativo (não deletado) obrigatoriamente.
     *
     * @param userId id do usuário
     * @return usuário encontrado
     */
    private ControlPlaneUser loadActiveUserRequired(Long userId) {
        return controlPlaneUserRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Usuário não encontrado",
                        404
                ));
    }

    /**
     * Valida se o usuário pertence à conta do Control Plane.
     *
     * @param controlPlaneUser usuário alvo
     * @param controlPlaneAccount conta única do Control Plane
     */
    private void validateUserBelongsToControlPlane(
            ControlPlaneUser controlPlaneUser,
            Account controlPlaneAccount
    ) {
        if (controlPlaneUser.getAccount() == null
                || controlPlaneUser.getAccount().getId() == null
                || !controlPlaneUser.getAccount().getId().equals(controlPlaneAccount.getId())) {
            throw new ApiException(
                    ApiErrorCode.USER_OUT_OF_SCOPE,
                    "Usuário não pertence ao Control Plane",
                    403
            );
        }
    }

    /**
     * Valida proteção de usuário BUILT_IN.
     *
     * @param controlPlaneUser usuário alvo
     */
    private void validateBuiltInUserImmutable(ControlPlaneUser controlPlaneUser) {
        if (controlPlaneUser.isBuiltInUser()) {
            throw new ApiException(
                    ApiErrorCode.USER_BUILT_IN_IMMUTABLE,
                    BUILTIN_IMMUTABLE_MESSAGE,
                    409
            );
        }
    }
}