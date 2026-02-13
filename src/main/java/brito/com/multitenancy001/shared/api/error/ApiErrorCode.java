package brito.com.multitenancy001.shared.api.error;

import org.springframework.http.HttpStatus;

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
    ROLE_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_EMAIL(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_CONFIRM_PASSWORD(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PASSWORD_MISMATCH(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_NAME(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    INVALID_COMPANY_NAME(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_COMPANY_DOC_TYPE(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INVALID_COMPANY_DOC_NUMBER(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // Tokens / password flow (faltavam)
    TOKEN_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(ApiErrorCategory.SECURITY, HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(ApiErrorCategory.SECURITY, HttpStatus.BAD_REQUEST),

    CURRENT_PASSWORD_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    NEW_PASSWORD_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    CONFIRM_PASSWORD_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // =========================
    // ACCOUNT
    // =========================
    ACCOUNT_NOT_FOUND(ApiErrorCategory.ACCOUNT, HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_ENABLED(ApiErrorCategory.ACCOUNT, HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_READY(ApiErrorCategory.ACCOUNT, HttpStatus.CONFLICT),

    BUILTIN_ACCOUNT_PROTECTED(ApiErrorCategory.ACCOUNT, HttpStatus.FORBIDDEN),
    ACCOUNT_DELETED(ApiErrorCategory.ACCOUNT, HttpStatus.GONE),
    BUILTIN_ACCOUNT_NO_BILLING(ApiErrorCategory.ACCOUNT, HttpStatus.CONFLICT),

    // =========================
    // ENTITLEMENTS
    // =========================
    ENTITLEMENTS_NOT_FOUND(ApiErrorCategory.ENTITLEMENTS, HttpStatus.INTERNAL_SERVER_ERROR),

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

    CURRENT_PASSWORD_INVALID(ApiErrorCategory.SECURITY, HttpStatus.BAD_REQUEST),

    AUTH_ERROR(ApiErrorCategory.SECURITY, HttpStatus.INTERNAL_SERVER_ERROR),

    // =========================
    // TENANT / MULTI-TENANCY
    // =========================
    TENANT_INVALID(ApiErrorCategory.TENANT, HttpStatus.NOT_FOUND),
    TENANT_SCHEMA_NOT_FOUND(ApiErrorCategory.TENANT, HttpStatus.NOT_FOUND),
    TENANT_TABLE_NOT_FOUND(ApiErrorCategory.TENANT, HttpStatus.NOT_FOUND),
    TENANT_CONTEXT_REQUIRED(ApiErrorCategory.TENANT, HttpStatus.INTERNAL_SERVER_ERROR),

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
    // PROVISIONING (reservado)
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

    // =========================
    // DOMAIN
    // =========================
    DOMAIN_RULE_VIOLATION(ApiErrorCategory.DOMAIN, HttpStatus.BAD_REQUEST),

    // =========================
    // TENANT DOMAIN: PRODUCTS / CATEGORIES (faltavam)
    // =========================
    PRODUCT_ID_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PRODUCT_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND(ApiErrorCategory.DOMAIN, HttpStatus.NOT_FOUND),
    PRODUCT_DELETED(ApiErrorCategory.DOMAIN, HttpStatus.CONFLICT),

    PRODUCT_NAME_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    PRODUCT_PRICE_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    SKU_ALREADY_EXISTS(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),

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
    CATEGORY_NAME_ALREADY_EXISTS(ApiErrorCategory.DATA_INTEGRITY, HttpStatus.CONFLICT),

    SUBCATEGORY_REQUIRED(ApiErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    SUBCATEGORY_NOT_FOUND(ApiErrorCategory.DOMAIN, HttpStatus.NOT_FOUND),
    INVALID_SUBCATEGORY(ApiErrorCategory.DOMAIN, HttpStatus.CONFLICT),

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
