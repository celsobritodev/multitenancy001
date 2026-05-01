package brito.com.multitenancy001.shared.validation;

import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * Validador central para campos textuais obrigatórios.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Eliminar {@code StringUtils.hasText(...)} repetido na application layer.</li>
 *   <li>Padronizar mensagens amigáveis ao cliente.</li>
 *   <li>Centralizar normalização simples de texto.</li>
 * </ul>
 */
public final class TextValidator {

    private TextValidator() {
    }

    public static void requireTenantSchema(String tenantSchema) {
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(
                    ApiErrorCode.TENANT_CONTEXT_REQUIRED,
                    "Contexto do tenant é obrigatório.",
                    400
            );
        }
    }

    public static void requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_NAME,
                    "Nome é obrigatório.",
                    400
            );
        }
    }

    public static void requireCategoryName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(
                    ApiErrorCode.CATEGORY_NAME_REQUIRED,
                    "Nome da categoria é obrigatório.",
                    400
            );
        }
    }

    public static void requireSubcategoryName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(
                    ApiErrorCode.SUBCATEGORY_NAME_REQUIRED,
                    "Nome da subcategoria é obrigatório.",
                    400
            );
        }
    }

    public static void requireSupplierName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(
                    ApiErrorCode.SUPPLIER_NAME_REQUIRED,
                    "Nome do fornecedor é obrigatório.",
                    400
            );
        }
    }

    public static void requireSupplierDocument(String document) {
        if (!StringUtils.hasText(document)) {
            throw new ApiException(
                    ApiErrorCode.SUPPLIER_DOCUMENT_REQUIRED,
                    "Documento do fornecedor é obrigatório.",
                    400
            );
        }
    }

    public static void requireSupplierEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException(
                    ApiErrorCode.SUPPLIER_EMAIL_REQUIRED,
                    "E-mail do fornecedor é obrigatório.",
                    400
            );
        }
    }

    public static void requireEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_EMAIL,
                    "E-mail é obrigatório.",
                    400
            );
        }
    }

    public static void requirePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_PASSWORD,
                    "Senha é obrigatória.",
                    400
            );
        }
    }

    /**
     * Exige nova senha para fluxos de reset/troca.
     *
     * @param newPassword nova senha
     */
    public static void requireNewPassword(String newPassword) {
        if (!StringUtils.hasText(newPassword)) {
            throw new ApiException(
                    ApiErrorCode.NEW_PASSWORD_REQUIRED,
                    "Nova senha é obrigatória.",
                    400
            );
        }
    }

    public static void requireSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SLUG,
                    "Slug é obrigatório.",
                    400
            );
        }
    }

    public static void requireSearchTerm(String term) {
        if (!StringUtils.hasText(term)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SEARCH,
                    "Termo de busca é obrigatório.",
                    400
            );
        }
    }

    /**
     * Normaliza string opcional:
     * trim + null quando vier vazia.
     *
     * @param value valor original
     * @return valor trimado ou null
     */
    public static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Alias semântico para compatibilidade com código já existente.
     *
     * @param value valor original
     * @return valor trimado ou null
     */
    public static String trimToNull(String value) {
        return normalizeNullable(value);
    }

    public static String requireAndTrimCategoryName(String name) {
        requireCategoryName(name);
        return name.trim();
    }

    public static String requireAndTrimSubcategoryName(String name) {
        requireSubcategoryName(name);
        return name.trim();
    }

    public static String requireAndTrimSupplierName(String name) {
        requireSupplierName(name);
        return name.trim();
    }

    public static String requireAndTrimDocument(String document) {
        requireSupplierDocument(document);
        return document.trim();
    }

    public static String requireAndTrimEmail(String email) {
        requireSupplierEmail(email);
        return email.trim();
    }
}