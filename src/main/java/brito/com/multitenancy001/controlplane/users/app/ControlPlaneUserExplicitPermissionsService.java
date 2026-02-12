package brito.com.multitenancy001.controlplane.users.app;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ControlPlaneUserExplicitPermissionsService {

    private static final String BUILTIN_IMMUTABLE_CODE = "USER_BUILT_IN_IMMUTABLE";
    private static final String BUILTIN_IMMUTABLE_MESSAGE =
            "Usuário BUILT_IN é protegido: não pode ter permissões alteradas; apenas senha pode ser trocada.";

    private static final String CP_ACCOUNT_INVALID_CODE = "CONTROLPLANE_ACCOUNT_INVALID";
    private static final String CP_ACCOUNT_INVALID_MESSAGE =
            "Configuração inválida: conta do Control Plane ausente ou duplicada.";

    private final PublicUnitOfWork publicUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    /**
     * Define permissions explícitas (override) a partir de strings.
     *
     * Regras:
     * - Só aceita "CP_*" (STRICT).
     * - Converte para enum ControlPlanePermission (explode se não existir).
     * - Só permite alterar usuários do Control Plane.
     * - Não permite alterar usuário deleted (deleted=false obrigatório).
     * - BUILT_IN: 409 USER_BUILT_IN_IMMUTABLE
     */
    public void setExplicitPermissionsFromCodes(Long userId, Collection<String> permissionCodes) {

        LinkedHashSet<String> normalized = PermissionScopeValidator.normalizeControlPlaneStrict(permissionCodes);

        Set<ControlPlanePermission> perms = normalized.stream()
                .map(code -> {
                    try {
                        return ControlPlanePermission.valueOf(code);
                    } catch (IllegalArgumentException e) {
                        throw new ApiException(
                                "INVALID_PERMISSION",
                                "Permission não existe no enum ControlPlanePermission: " + code,
                                400
                        );
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        publicUnitOfWork.tx(() -> {

            final Account cp;
            try {
                cp = accountRepository.getSingleControlPlaneAccount();
            } catch (IllegalStateException e) {
                throw new ApiException(
                        CP_ACCOUNT_INVALID_CODE,
                        CP_ACCOUNT_INVALID_MESSAGE + " " + e.getMessage(),
                        500
                );
            }

            if (userId == null) {
                throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
            }

            // NOT DELETED por contrato (deleted=false)
            ControlPlaneUser user = controlPlaneUserRepository.findByIdAndDeletedFalse(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            // escopo CP garantido
            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("USER_OUT_OF_SCOPE", "Usuário não pertence ao Control Plane", 403);
            }

            if (user.isBuiltInUser()) {
                throw new ApiException(BUILTIN_IMMUTABLE_CODE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            user.replaceExplicitPermissions(perms);
            controlPlaneUserRepository.save(user);
            return null;
        });
    }
}
