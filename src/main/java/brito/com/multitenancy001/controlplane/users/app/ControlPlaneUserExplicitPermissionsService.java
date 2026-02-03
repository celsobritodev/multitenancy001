package brito.com.multitenancy001.controlplane.users.app;

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

    private final PublicUnitOfWork publicUnitOfWork;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    /**
     * Define permissions explícitas (override) a partir de strings.
     *
     * Regras:
     * - Só aceita "CP_*" (STRICT).
     * - Converte para enum ControlPlanePermission (explode se não existir).
     * - Persiste no usuário (public schema).
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
            ControlPlaneUser user = controlPlaneUserRepository.findByIdAndDeletedFalse(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            user.setPermissions(perms);
            controlPlaneUserRepository.save(user);
            return null;
        });
    }
}
