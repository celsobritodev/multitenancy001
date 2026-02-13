package brito.com.multitenancy001.shared.api.error;

import org.springframework.http.HttpStatus;

/**
 * Fonte de verdade para códigos de erro da API.
 *
 * - Enum-only (governança / documentação / observabilidade)
 * - Cada código define: categoria semântica + status HTTP padrão
 * - Não use strings mágicas em throw new ApiException(...)
 */
public enum ApiErrorCode {

    // =========================
    // VALIDATION
    // =========================
    VALIDATION_ERROR(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_REQUEST_BODY(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_ENUM(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_RANGE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    RANGE_TOO_LARGE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_SEARCH(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_SLUG(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_STATUS(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_LOGIN(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_REFRESH(ApiErrorCategory.SECURITY, HttpStatus.UNAUTHORIZED),

    ACCOUNT_ID_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    ACCOUNT_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    STATUS_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    ACCOUNT_STATUSES_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    DATE_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    DATE_RANGE_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    PAYMENT_STATUS_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PAYMENT_ID_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_TRANSACTION_ID(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_DATE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_PERMISSION(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    USER_ID_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    USER_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    ROLE_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_EMAIL(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_CONFIRM_PASSWORD(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PASSWORD_MISMATCH(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    WEAK_PASSWORD(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_NAME(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_COMPANY_NAME(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_COMPANY_DOC_TYPE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_COMPANY_DOC_NUMBER(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // Tokens / password flow
    TOKEN_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(ApiErrorCategory.SECURITY, HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(ApiErrorCategory.SECURITY, HttpStatus.BAD_REQUEST),

    CURRENT_PASSWORD_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    NEW_PASSWORD_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    CONFIRM_PASSWORD_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    CURRENT_PASSWORD_INVALID(ApiErrorCategory.SECURITY, HttpStatus.BAD_REQUEST),

    // Auth flow / seleção
    INVALID_ACCOUNT(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_CHALLENGE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    CHALLENGE_NOT_FOUND(ApiErrorCategory.SECURITY, HttpStatus.NOT_FOUND),
    INVALID_SELECTION(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_REQUEST(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_ROLE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_ORIGIN(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // transfer / deltas (usos atuais)
    FROM_USER_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    TO_USER_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_TRANSFER(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_STORAGE_DELTA(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // outros validadores de domínio (aparecem no código)
    INVALID_ENTITLEMENT(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_LEAD_TIME(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_RATING(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // =========================
    // ACCOUNT
    // =========================
    ACCOUNT_NOT_FOUND(ApiErrorCategory.ACCOUNT, HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_ENABLED(ApiErrorCategory.ACCOUNT, HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_READY(ApiErrorCategory.ACCOUNT, HttpStatus.CONFLICT),

    ACCOUNT_INACTIVE(ApiErrorCategory.ACCOUNT, HttpStatus.FORBIDDEN),

    BUILTIN_ACCOUNT_PROTECTED(ApiErrorCategory.ACCOUNT, HttpStatus.FORBIDDEN),
    ACCOUNT_DELETED(ApiErrorCategory.ACCOUNT, HttpStatus.GONE),
    BUILTIN_ACCOUNT_NO_BILLING(ApiErrorCategory.ACCOUNT, HttpStatus.CONFLICT),

    // =========================
    // ENTITLEMENTS
    // =========================
    ENTITLEMENTS_NOT_FOUND(ApiErrorCategory.ENTITLEMENTS, HttpStatus.INTERNAL_SERVER_ERROR),
    ENTITLEMENTS_UNEXPECTED_NULL(ApiErrorCategory.ENTITLEMENTS, HttpStatus.INTERNAL_SERVER_ERROR),

    QUOTA_MAX_USERS_REACHED(ApiErrorCategory.ENTITLEMENTS, HttpStatus.CONFLICT),
    QUOTA_MAX_PRODUCTS_REACHED(ApiErrorCategory.ENTITLEMENTS, HttpStatus.CONFLICT),
    QUOTA_MAX_STORAGE_REACHED(ApiErrorCategory.ENTITLEMENTS, HttpStatus.CONFLICT),

    // =========================
    // SECURITY / AUTH
    // =========================
    INVALID_USER(ApiErrorCategory.SECURITY, HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(ApiErrorCategory.SECURITY, HttpStatus.NOT_FOUND),
    USER_NOT_ENABLED(ApiErrorCategory.SECURITY, HttpStatus.NOT_FOUND),
    USER_INACTIVE(ApiErrorCategory.SECURITY, HttpStatus.FORBIDDEN),

    ACCESS_DENIED(ApiErrorCategory.SECURITY, HttpStatus.FORBIDDEN),
    UNAUTHENTICATED(ApiErrorCategory.SECURITY, HttpStatus.UNAUTHORIZED),
    FORBIDDEN(ApiErrorCategory.SECURITY, HttpStatus.FORBIDDEN),

    USER_OUT_OF_SCOPE(ApiErrorCategory.SECURITY, HttpStatus.FORBIDDEN),
    USER_BUILT_IN_IMMUTABLE(ApiErrorCategory.SECURITY, HttpStatus.CONFLICT),

    CONTROLPLANE_ACCOUNT_INVALID(ApiErrorCategory.SECURITY, HttpStatus.INTERNAL_SERVER_ERROR),

    EMAIL_RESERVED(ApiErrorCategory.SECURITY, HttpStatus.CONFLICT),
    EMAIL_ALREADY_IN_USE(ApiErrorCategory.SECURITY, HttpStatus.CONFLICT),

    AUTH_ERROR(ApiErrorCategory.SECURITY, HttpStatus.INTERNAL_SERVER_ERROR),

    // =========================
    // TENANT / MULTI-TENANCY
    // =========================
    TENANT_INVALID(ApiErrorCategory.TENANT, HttpStatus.NOT_FOUND),
    TENANT_SCHEMA_NOT_FOUND(ApiErrorCategory.TENANT, HttpStatus.NOT_FOUND),
    TENANT_TABLE_NOT_FOUND(ApiErrorCategory.TENANT, HttpStatus.NOT_FOUND),
    TENANT_CONTEXT_REQUIRED(ApiErrorCategory.TENANT, HttpStatus.UNAUTHORIZED),

    /**
     * Conta do ControlPlane não tem tenantSchema associado (estado inconsistente).
     */
    TENANT_SCHEMA_REQUIRED(ApiErrorCategory.TENANT, HttpStatus.CONFLICT),

    SCHEMA_REQUIRED(ApiErrorCategory.TENANT, HttpStatus.BAD_REQUEST),
    SCHEMA_INVALID(ApiErrorCategory.TENANT, HttpStatus.BAD_REQUEST),
    TABLE_REQUIRED(ApiErrorCategory.TENANT, HttpStatus.BAD_REQUEST),
    TABLE_INVALID(ApiErrorCategory.TENANT, HttpStatus.BAD_REQUEST),

    TENANT_SCHEMA_DROP_FAILED(ApiErrorCategory.TENANT, HttpStatus.INTERNAL_SERVER_ERROR),
    TENANT_SCHEMA_EXISTS_CHECK_FAILED(ApiErrorCategory.TENANT, HttpStatus.INTERNAL_SERVER_ERROR),
    TENANT_TABLE_EXISTS_CHECK_FAILED(ApiErrorCategory.TENANT, HttpStatus.INTERNAL_SERVER_ERROR),
    TENANT_SCHEMA_PROVISIONING_FAILED(ApiErrorCategory.TENANT, HttpStatus.INTERNAL_SERVER_ERROR),
    TENANT_SCHEMA_LOCK_TIMEOUT(ApiErrorCategory.TENANT, HttpStatus.CONFLICT),

    // =========================
    // BILLING
    // =========================
    PAYMENT_NOT_FOUND(ApiErrorCategory.BILLING, HttpStatus.NOT_FOUND),
    PAYMENT_FAILED(ApiErrorCategory.BILLING, HttpStatus.PAYMENT_REQUIRED),
    INVALID_PAYMENT_STATUS(ApiErrorCategory.BILLING, HttpStatus.CONFLICT),
    PAYMENT_NOT_REFUNDABLE(ApiErrorCategory.BILLING, HttpStatus.CONFLICT),
    PAYMENT_ALREADY_EXISTS(ApiErrorCategory.BILLING, HttpStatus.CONFLICT),
    PAYMENT_ERROR(ApiErrorCategory.BILLING, HttpStatus.BAD_REQUEST),

    // =========================
    // PROVISIONING
    // =========================
    PROVISIONING_ERROR(ApiErrorCategory.PROVISIONING, HttpStatus.INTERNAL_SERVER_ERROR),

    // =========================
    // DATA INTEGRITY / UNIQUE
    // =========================
    DUPLICATE_ENTRY(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),
    DUPLICATE_EMAIL(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),
    DUPLICATE_SLUG(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),
    DUPLICATE_SCHEMA(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),
    DUPLICATE_NUMBER(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),

    SKU_ALREADY_EXISTS(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),
    CATEGORY_NAME_ALREADY_EXISTS(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),

    SUBCATEGORY_ALREADY_EXISTS(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),
    SUPPLIER_DOCUMENT_ALREADY_EXISTS(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),

    // =========================
    // DOMAIN
    // =========================
    DOMAIN_RULE_VIOLATION(ApiErrorCategory.DOMAIN, HttpStatus.BAD_REQUEST),

    TENANT_OWNER_REQUIRED(ApiErrorCategory.DOMAIN, HttpStatus.CONFLICT),

    // Tenant domain: products / categories / subcategories / suppliers
    PRODUCT_ID_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PRODUCT_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND(ApiErrorCategory.DOMAIN, HttpStatus.NOT_FOUND),
    PRODUCT_DELETED(ApiErrorCategory.DOMAIN, HttpStatus.CONFLICT),
    PRODUCT_NAME_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PRODUCT_PRICE_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_PRICE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PRICE_TOO_HIGH(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_STOCK(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_STOCK_RANGE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_PRICE_RANGE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_BRAND(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    CATEGORY_ID_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    CATEGORY_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    CATEGORY_NOT_FOUND(ApiErrorCategory.DOMAIN, HttpStatus.NOT_FOUND),
    CATEGORY_DELETED(ApiErrorCategory.DOMAIN, HttpStatus.CONFLICT),
    CATEGORY_NAME_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    SUBCATEGORY_ID_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    SUBCATEGORY_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    SUBCATEGORY_NOT_FOUND(ApiErrorCategory.DOMAIN, HttpStatus.NOT_FOUND),
    SUBCATEGORY_DELETED(ApiErrorCategory.DOMAIN, HttpStatus.CONFLICT),
    SUBCATEGORY_NAME_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_SUBCATEGORY(ApiErrorCategory.DOMAIN, HttpStatus.CONFLICT),

    SUPPLIER_ID_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    SUPPLIER_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    SUPPLIER_NOT_FOUND(ApiErrorCategory.DOMAIN, HttpStatus.NOT_FOUND),
    SUPPLIER_DELETED(ApiErrorCategory.DOMAIN, HttpStatus.NOT_FOUND),
    SUPPLIER_NAME_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    SUPPLIER_EMAIL_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    SUPPLIER_DOCUMENT_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // =========================
    // SYSTEM
    // =========================
    INTERNAL_SERVER_ERROR(ApiErrorCategory.SYSTEM, HttpStatus.INTERNAL_SERVER_ERROR);

    private final ApiErrorCategory category;
    private final HttpStatus defaultStatus;

    ApiErrorCode(ApiErrorCategory category, HttpStatus defaultStatus) {
        this.category = category;
        this.defaultStatus = defaultStatus;
    }

    public String code() {
        return name();
    }

    public ApiErrorCategory category() {
        return category;
    }

    public int defaultHttpStatus() {
        return defaultStatus.value();
    }
}
