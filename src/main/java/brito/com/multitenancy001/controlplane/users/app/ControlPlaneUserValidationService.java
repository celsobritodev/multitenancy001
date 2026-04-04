package brito.com.multitenancy001.controlplane.users.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneBuiltInUsers;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * Serviço responsável pelas validações transversais do módulo de usuários do Control Plane.
 */
@Service
public class ControlPlaneUserValidationService {

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
     * Garante que o usuário não seja built-in imutável.
     *
     * @param user usuário alvo
     */
    public void assertMutableUser(ControlPlaneUser user) {
        if (user != null && user.isBuiltInUser()) {
            throw new ApiException(
                    ApiErrorCode.USER_BUILT_IN_IMMUTABLE,
                    ControlPlaneUserInternalFacade.BUILTIN_IMMUTABLE_MESSAGE,
                    409
            );
        }
    }
}