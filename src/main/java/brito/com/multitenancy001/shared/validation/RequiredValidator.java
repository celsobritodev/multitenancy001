package brito.com.multitenancy001.shared.validation;

import java.util.UUID;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * Validador central para obrigatoriedade simples na application layer.
 */
public final class RequiredValidator {

    private RequiredValidator() {
    }

    public static void requirePayload(Object payload, ApiErrorCode errorCode, String message) {
        if (payload == null) {
            throw new ApiException(errorCode, message);
        }
    }

    public static void requireAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(
                    ApiErrorCode.ACCOUNT_ID_REQUIRED,
                    "Conta é obrigatória para esta operação."
            );
        }
    }

    public static void requireUserId(Long userId) {
        if (userId == null) {
            throw new ApiException(
                    ApiErrorCode.USER_ID_REQUIRED,
                    "Usuário é obrigatório para esta operação."
            );
        }
    }

    public static void requireToUserId(Long userId) {
        if (userId == null) {
            throw new ApiException(
                    ApiErrorCode.TO_USER_REQUIRED,
                    "Usuário de destino é obrigatório."
            );
        }
    }

    public static void requireSupplierId(UUID supplierId) {
        if (supplierId == null) {
            throw new ApiException(
                    ApiErrorCode.SUPPLIER_ID_REQUIRED,
                    "Fornecedor é obrigatório para esta operação."
            );
        }
    }

    public static void requireRole(Object role) {
        if (role == null) {
            throw new ApiException(
                    ApiErrorCode.ROLE_REQUIRED,
                    "Perfil é obrigatório para esta operação."
            );
        }
    }
}