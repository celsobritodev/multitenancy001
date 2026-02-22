// src/main/java/brito/com/multitenancy001/shared/api/error/ApiErrorCode.java
package brito.com.multitenancy001.shared.api.error;

import java.util.Locale;
import java.util.Optional;

/**
 * Catálogo tipado de códigos de erro.
 *
 * - name() é o "código externo" (ex.: ACCOUNT_NOT_FOUND)
 * - category é organização semântica
 * - httpStatus é o status padrão
 * - defaultMessage é uma mensagem padrão (pode ser sobrescrita)
 */
public enum ApiErrorCode {

    // =========================
    // Accounts (Control Plane)
    // =========================
    ACCOUNT_REQUIRED(ApiErrorCategory.ACCOUNTS, 400, "Conta é obrigatória"),
    ACCOUNT_ID_REQUIRED(ApiErrorCategory.ACCOUNTS, 400, "accountId é obrigatório"),
    ACCOUNT_NOT_FOUND(ApiErrorCategory.ACCOUNTS, 404, "Conta não encontrada"),
    ACCOUNT_NOT_ENABLED(ApiErrorCategory.ACCOUNTS, 404, "Conta não encontrada ou não operacional"),
    ACCOUNT_NOT_READY(ApiErrorCategory.ACCOUNTS, 409, "Conta não está pronta"),
    ACCOUNT_INACTIVE(ApiErrorCategory.ACCOUNTS, 409, "Conta inativa"),
    ACCOUNT_DELETED(ApiErrorCategory.ACCOUNTS, 409, "Conta removida"),
    ACCOUNT_STATUSES_REQUIRED(ApiErrorCategory.ACCOUNTS, 400, "statuses é obrigatório"),
    BUILTIN_ACCOUNT_PROTECTED(ApiErrorCategory.SECURITY, 403, "Operação não permitida para contas do sistema"),
    BUILTIN_ACCOUNT_NO_BILLING(ApiErrorCategory.SECURITY, 403, "Conta do sistema não possui billing"),

    INVALID_ACCOUNT(ApiErrorCategory.ACCOUNTS, 400, "Conta inválida"),

    ENTITLEMENTS_NOT_FOUND(ApiErrorCategory.ACCOUNTS, 404, "Entitlements não encontrados"),

    // =========================
    // Users
    // =========================
    USER_REQUIRED(ApiErrorCategory.USERS, 400, "Usuário é obrigatório"),
    USER_ID_REQUIRED(ApiErrorCategory.USERS, 400, "userId é obrigatório"),
    USER_NOT_FOUND(ApiErrorCategory.USERS, 404, "Usuário não encontrado"),
    USER_NOT_ENABLED(ApiErrorCategory.USERS, 403, "Usuário não habilitado"),
    USER_INACTIVE(ApiErrorCategory.USERS, 403, "Usuário inativo"),
    USER_OUT_OF_SCOPE(ApiErrorCategory.SECURITY, 403, "Usuário fora do escopo"),

    ROLE_REQUIRED(ApiErrorCategory.USERS, 400, "role é obrigatório"),
    FROM_USER_REQUIRED(ApiErrorCategory.USERS, 400, "fromUser é obrigatório"),
    TO_USER_REQUIRED(ApiErrorCategory.USERS, 400, "toUser é obrigatório"),

    // ✅ ADICIONADOS (para remover string code)
    USER_BUILT_IN_IMMUTABLE(ApiErrorCategory.SECURITY, 409,
            "Usuário BUILT_IN é protegido: não pode ter permissões alteradas; apenas senha pode ser trocada."),
    CONTROLPLANE_ACCOUNT_INVALID(ApiErrorCategory.INTERNAL, 500,
            "Configuração inválida: conta do Control Plane ausente ou duplicada."),
    INVALID_PERMISSION(ApiErrorCategory.SECURITY, 400, "Permissão inválida"),

    // ✅ ADICIONADO (para regras de ownership no Tenant)
    TENANT_OWNER_REQUIRED(ApiErrorCategory.CONFLICT, 409,
            "Tenant deve possuir ao menos um owner ativo"),

    // =========================
    // Auth / Security
    // =========================
    INVALID_CREDENTIALS(ApiErrorCategory.AUTH, 401, "Usuário ou senha inválidos"),
    INVALID_LOGIN(ApiErrorCategory.AUTH, 401, "Login inválido"),
    INVALID_USER(ApiErrorCategory.AUTH, 401, "Usuário inválido"),
    UNAUTHENTICATED(ApiErrorCategory.AUTH, 401, "Não autenticado"),
    UNAUTHORIZED(ApiErrorCategory.SECURITY, 401, "Não autorizado"),
    FORBIDDEN(ApiErrorCategory.SECURITY, 403, "Acesso negado"),
    ACCESS_DENIED(ApiErrorCategory.SECURITY, 403, "Acesso negado"),
    CHALLENGE_NOT_FOUND(ApiErrorCategory.AUTH, 404, "Challenge não encontrado, expirado ou já usado"),

    INVALID_REFRESH(ApiErrorCategory.AUTH, 401, "Refresh inválido"),
    INVALID_CHALLENGE(ApiErrorCategory.AUTH, 400, "Challenge inválido"),
    INVALID_SELECTION(ApiErrorCategory.AUTH, 400, "Seleção inválida"),

    TOKEN_REQUIRED(ApiErrorCategory.AUTH, 401, "Token é obrigatório"),
    TOKEN_INVALID(ApiErrorCategory.AUTH, 401, "Token inválido"),
    TOKEN_EXPIRED(ApiErrorCategory.AUTH, 401, "Token expirado"),
    INVALID_TOKEN(ApiErrorCategory.AUTH, 401, "Token inválido"),

    AUTH_ERROR(ApiErrorCategory.AUTH, 500, "Erro de autenticação"),
    
    MUST_CHANGE_PASSWORD(ApiErrorCategory.AUTH, 428, "É necessário trocar a senha antes de continuar"),


    // Password
    INVALID_PASSWORD(ApiErrorCategory.AUTH, 400, "Senha inválida"),
    WEAK_PASSWORD(ApiErrorCategory.AUTH, 400, "Senha fraca"),
    PASSWORD_MISMATCH(ApiErrorCategory.AUTH, 400, "Senhas não conferem"),
    CURRENT_PASSWORD_REQUIRED(ApiErrorCategory.AUTH, 400, "Senha atual é obrigatória"),
    CURRENT_PASSWORD_INVALID(ApiErrorCategory.AUTH, 400, "Senha atual inválida"),
    NEW_PASSWORD_REQUIRED(ApiErrorCategory.AUTH, 400, "Nova senha é obrigatória"),
    CONFIRM_PASSWORD_REQUIRED(ApiErrorCategory.AUTH, 400, "Confirmação de senha é obrigatória"),
    INVALID_CONFIRM_PASSWORD(ApiErrorCategory.AUTH, 400, "Confirmação de senha inválida"),

    // Email
    INVALID_EMAIL(ApiErrorCategory.VALIDATION, 400, "E-mail inválido"),
    EMAIL_ALREADY_EXISTS(ApiErrorCategory.CONFLICT, 409, "E-mail já existe"),
    EMAIL_ALREADY_IN_USE(ApiErrorCategory.CONFLICT, 409, "E-mail já está em uso"),
    EMAIL_RESERVED(ApiErrorCategory.CONFLICT, 409, "E-mail reservado"),

    // =========================
    // Tenant / Multitenancy
    // =========================
    TENANT_SELECTION_REQUIRED(ApiErrorCategory.AUTH, 400, "Seleção de tenant é obrigatória"),

    TENANT_CONTEXT_REQUIRED(ApiErrorCategory.TENANT, 400, "Tenant context é obrigatório"),
    TENANT_INVALID(ApiErrorCategory.TENANT, 400, "Tenant inválido"),
    TENANT_SCHEMA_NOT_FOUND(ApiErrorCategory.TENANT, 404, "Tenant schema não encontrado"),
    TENANT_TABLE_NOT_FOUND(ApiErrorCategory.TENANT, 404, "Tabela do tenant não encontrada"),

    // ✅ ADICIONADOS (para remover string code no provisioning worker)
    TENANT_SCHEMA_LOCK_TIMEOUT(ApiErrorCategory.CONFLICT, 409,
            "Não foi possível obter lock de provisionamento do schema do tenant"),
    TENANT_SCHEMA_PROVISIONING_FAILED(ApiErrorCategory.INTERNAL, 500,
            "Falha ao provisionar schema do tenant"),
    TENANT_SCHEMA_DROP_FAILED(ApiErrorCategory.INTERNAL, 500,
            "Falha ao dropar schema do tenant"),
    TENANT_SCHEMA_EXISTS_CHECK_FAILED(ApiErrorCategory.INTERNAL, 500,
            "Falha ao verificar existência do schema do tenant"),
    TENANT_TABLE_EXISTS_CHECK_FAILED(ApiErrorCategory.INTERNAL, 500,
            "Falha ao verificar existência de tabela do tenant"),

    SCHEMA_REQUIRED(ApiErrorCategory.TENANT, 400, "Schema é obrigatório"),
    SCHEMA_INVALID(ApiErrorCategory.TENANT, 400, "Schema inválido"),
    TABLE_REQUIRED(ApiErrorCategory.TENANT, 400, "Table é obrigatório"),
    TABLE_INVALID(ApiErrorCategory.TENANT, 400, "Table inválido"),

    // =========================
    // Billing / Payments
    // =========================
    PAYMENT_ID_REQUIRED(ApiErrorCategory.BILLING, 400, "paymentId é obrigatório"),
    PAYMENT_NOT_FOUND(ApiErrorCategory.BILLING, 404, "Pagamento não encontrado"),
    PAYMENT_FAILED(ApiErrorCategory.BILLING, 409, "Pagamento falhou"),
    PAYMENT_ALREADY_EXISTS(ApiErrorCategory.CONFLICT, 409, "Pagamento já existe"),
    PAYMENT_STATUS_REQUIRED(ApiErrorCategory.BILLING, 400, "status do pagamento é obrigatório"),
    PAYMENT_NOT_REFUNDABLE(ApiErrorCategory.BILLING, 409, "Pagamento não reembolsável"),
    INVALID_PAYMENT_STATUS(ApiErrorCategory.BILLING, 400, "Status de pagamento inválido"),
    INVALID_TRANSACTION_ID(ApiErrorCategory.BILLING, 400, "TransactionId inválido"),

    // =========================
    // Products
    // =========================
    PRODUCT_REQUIRED(ApiErrorCategory.PRODUCTS, 400, "Produto é obrigatório"),
    PRODUCT_ID_REQUIRED(ApiErrorCategory.PRODUCTS, 400, "productId é obrigatório"),
    PRODUCT_NOT_FOUND(ApiErrorCategory.PRODUCTS, 404, "Produto não encontrado"),
    PRODUCT_DELETED(ApiErrorCategory.PRODUCTS, 409, "Produto removido"),
    PRODUCT_NAME_REQUIRED(ApiErrorCategory.PRODUCTS, 400, "Nome do produto é obrigatório"),
    PRODUCT_PRICE_REQUIRED(ApiErrorCategory.PRODUCTS, 400, "Preço do produto é obrigatório"),
    SKU_ALREADY_EXISTS(ApiErrorCategory.CONFLICT, 409, "SKU já existe"),
    INVALID_PRICE(ApiErrorCategory.PRODUCTS, 400, "Preço inválido"),
    INVALID_PRICE_RANGE(ApiErrorCategory.PRODUCTS, 400, "Intervalo de preço inválido"),
    PRICE_TOO_HIGH(ApiErrorCategory.PRODUCTS, 400, "Preço alto demais"),
    INVALID_AMOUNT(ApiErrorCategory.PRODUCTS, 400, "Valor inválido"),
    INVALID_BRAND(ApiErrorCategory.PRODUCTS, 400, "Marca inválida"),
    BRAND_REQUIRED(ApiErrorCategory.PRODUCTS, 400, "Marca é obrigatória"),
    INVALID_STOCK(ApiErrorCategory.PRODUCTS, 400, "Estoque inválido"),
    INVALID_STOCK_RANGE(ApiErrorCategory.PRODUCTS, 400, "Intervalo de estoque inválido"),
    INVALID_LEAD_TIME(ApiErrorCategory.PRODUCTS, 400, "Lead time inválido"),
    INVALID_STORAGE_DELTA(ApiErrorCategory.PRODUCTS, 400, "Delta de estoque inválido"),
    
    // =========================
    // Generic fields (reutilizáveis)
    // =========================
    SKU_REQUIRED(ApiErrorCategory.PRODUCTS, 400, "sku é obrigatório"),
    PRICE_REQUIRED(ApiErrorCategory.PRODUCTS, 400, "price é obrigatório"),
    PRICE_RANGE_REQUIRED(ApiErrorCategory.PRODUCTS, 400, "minPrice e maxPrice são obrigatórios"),
    STOCK_CHANGE_REQUIRED(ApiErrorCategory.PRODUCTS, 400, "quantityChange é obrigatório"),


    // =========================
    // Categories / Subcategories
    // =========================
    CATEGORY_REQUIRED(ApiErrorCategory.CATEGORIES, 400, "Categoria é obrigatória"),
    CATEGORY_ID_REQUIRED(ApiErrorCategory.CATEGORIES, 400, "categoryId é obrigatório"),
    CATEGORY_NOT_FOUND(ApiErrorCategory.CATEGORIES, 404, "Categoria não encontrada"),
    CATEGORY_DELETED(ApiErrorCategory.CATEGORIES, 409, "Categoria removida"),
    CATEGORY_NAME_REQUIRED(ApiErrorCategory.CATEGORIES, 400, "Nome da categoria é obrigatório"),
    CATEGORY_NAME_ALREADY_EXISTS(ApiErrorCategory.CONFLICT, 409, "Nome da categoria já existe"),

    SUBCATEGORY_REQUIRED(ApiErrorCategory.SUBCATEGORIES, 400, "Subcategoria é obrigatória"),
    SUBCATEGORY_ID_REQUIRED(ApiErrorCategory.SUBCATEGORIES, 400, "subcategoryId é obrigatório"),
    SUBCATEGORY_NOT_FOUND(ApiErrorCategory.SUBCATEGORIES, 404, "Subcategoria não encontrada"),
    SUBCATEGORY_DELETED(ApiErrorCategory.SUBCATEGORIES, 409, "Subcategoria removida"),
    SUBCATEGORY_ALREADY_EXISTS(ApiErrorCategory.CONFLICT, 409, "Subcategoria já existe"),
    SUBCATEGORY_NAME_REQUIRED(ApiErrorCategory.SUBCATEGORIES, 400, "Nome da subcategoria é obrigatório"),
    INVALID_SUBCATEGORY(ApiErrorCategory.SUBCATEGORIES, 400, "Subcategoria inválida"),

    // =========================
    // Suppliers
    // =========================
    SUPPLIER_REQUIRED(ApiErrorCategory.SUPPLIERS, 400, "Fornecedor é obrigatório"),
    SUPPLIER_ID_REQUIRED(ApiErrorCategory.SUPPLIERS, 400, "supplierId é obrigatório"),
    SUPPLIER_NOT_FOUND(ApiErrorCategory.SUPPLIERS, 404, "Fornecedor não encontrado"),
    SUPPLIER_DELETED(ApiErrorCategory.SUPPLIERS, 409, "Fornecedor removido"),
    SUPPLIER_NAME_REQUIRED(ApiErrorCategory.SUPPLIERS, 400, "Nome do fornecedor é obrigatório"),
    SUPPLIER_EMAIL_REQUIRED(ApiErrorCategory.SUPPLIERS, 400, "E-mail do fornecedor é obrigatório"),
    SUPPLIER_DOCUMENT_REQUIRED(ApiErrorCategory.SUPPLIERS, 400, "Documento do fornecedor é obrigatório"),
    SUPPLIER_DOCUMENT_ALREADY_EXISTS(ApiErrorCategory.CONFLICT, 409, "Documento do fornecedor já existe"),

    // =========================
    // Generic validation / request
    // =========================
    INVALID_REQUEST(ApiErrorCategory.REQUEST, 400, "Requisição inválida"),
    VALIDATION_ERROR(ApiErrorCategory.VALIDATION, 400, "Erro de validação"),

    INVALID_NAME(ApiErrorCategory.VALIDATION, 400, "Nome inválido"),
    INVALID_SLUG(ApiErrorCategory.VALIDATION, 400, "Slug inválido"),
    INVALID_STATUS(ApiErrorCategory.VALIDATION, 400, "Status inválido"),
    STATUS_REQUIRED(ApiErrorCategory.VALIDATION, 400, "Status é obrigatório"),
    INVALID_SEARCH(ApiErrorCategory.VALIDATION, 400, "Busca inválida"),

    INVALID_RANGE(ApiErrorCategory.VALIDATION, 400, "Intervalo inválido"),
    RANGE_TOO_LARGE(ApiErrorCategory.VALIDATION, 400, "Intervalo grande demais"),

    DATE_REQUIRED(ApiErrorCategory.VALIDATION, 400, "Data é obrigatória"),
    DATE_RANGE_REQUIRED(ApiErrorCategory.VALIDATION, 400, "Intervalo de datas é obrigatório"),
    INVALID_DATE(ApiErrorCategory.VALIDATION, 400, "Data inválida"),
    INVALID_DATE_RANGE(ApiErrorCategory.VALIDATION, 400, "Intervalo de datas inválido"),

    INVALID_COMPANY_NAME(ApiErrorCategory.VALIDATION, 400, "Nome da empresa inválido"),
    INVALID_COMPANY_DOC_TYPE(ApiErrorCategory.VALIDATION, 400, "Tipo de documento inválido"),
    INVALID_COMPANY_DOC_NUMBER(ApiErrorCategory.VALIDATION, 400, "Número de documento inválido"),

    INVALID_ORIGIN(ApiErrorCategory.VALIDATION, 400, "Origem inválida"),
    INVALID_RATING(ApiErrorCategory.VALIDATION, 400, "Rating inválido"),

    INVALID_ROLE(ApiErrorCategory.USERS, 400, "Role inválida"),
    INVALID_TRANSFER(ApiErrorCategory.USERS, 400, "Transfer inválido"),

    // =========================
    // Quotas / Entitlements
    // =========================
    QUOTA_MAX_USERS_REACHED(ApiErrorCategory.QUOTAS, 409, "Limite de usuários atingido"),
    QUOTA_MAX_STORAGE_REACHED(ApiErrorCategory.QUOTAS, 409, "Limite de storage atingido"),
    QUOTA_MAX_PRODUCTS_REACHED(ApiErrorCategory.QUOTAS, 409, "Limite de produtos atingido"),
    INVALID_ENTITLEMENT(ApiErrorCategory.ENTITLEMENTS, 400, "Entitlement inválido"),
    ENTITLEMENTS_UNEXPECTED_NULL(ApiErrorCategory.ENTITLEMENTS, 500, "Entitlements inesperadamente nulo"),

    // =========================
    // Conflict
    // =========================
    DUPLICATE_ENTRY(ApiErrorCategory.CONFLICT, 409, "Registro duplicado"),

    // =========================
    // Internal
    // =========================
    INTERNAL_ERROR(ApiErrorCategory.INTERNAL, 500, "Erro interno"),
    INTERNAL_SERVER_ERROR(ApiErrorCategory.INTERNAL, 500, "Erro interno inesperado"),
    FEATURE_NOT_IMPLEMENTED(ApiErrorCategory.INTERNAL, 501, "Funcionalidade ainda não implementada");

    private final ApiErrorCategory category;
    private final int httpStatus;
    private final String defaultMessage;

    ApiErrorCode(ApiErrorCategory category, int httpStatus, String defaultMessage) {
        this.category = category;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public ApiErrorCategory category() {
        return category;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /**
     * Parse seguro a partir de um código legado (String).
     *
     * Exemplos aceitos:
     * - "account_not_found"
     * - "ACCOUNT-NOT-FOUND"
     * - " account not found "
     * - "AccountNotFound" (best-effort)
     */
    public static Optional<ApiErrorCode> tryParse(String legacyCode) {
        if (legacyCode == null || legacyCode.isBlank()) return Optional.empty();

        String s = legacyCode.trim();

        // 1) normaliza separadores comuns para "_"
        s = s.replace('-', '_')
             .replace(' ', '_')
             .replace('.', '_')
             .replace('/', '_');

        // 2) camelCase/PascalCase -> snake-ish (best effort)
        // "AccountNotFound" => "Account_Not_Found"
        s = s.replaceAll("([a-z])([A-Z])", "$1_$2");

        // 3) collapse múltiplos underscores
        s = s.replaceAll("_+", "_");

        // 4) upper
        String normalized = s.toUpperCase(Locale.ROOT);

        try {
            return Optional.of(ApiErrorCode.valueOf(normalized));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
