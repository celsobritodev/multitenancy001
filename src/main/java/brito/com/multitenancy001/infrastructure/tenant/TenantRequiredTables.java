package brito.com.multitenancy001.infrastructure.tenant;

/**
 * Lista central de tabelas "âncora" do schema TENANT.
 *
 * Uso típico:
 * - tenantExecutor.runInSchemaOrThrow(schema, TenantRequiredTables.TENANT_USERS, () -> ...)
 * - tenantExecutor.runInSchemaIfReady(schema, TenantRequiredTables.PRODUCTS, () -> ..., defaultValue)
 *
 * Dica:
 * - escolha UMA tabela bem “core” (ex: TENANT_USERS) como "schema ready"
 * - use outras quando o caso exigir (ex: PRODUCTS)
 */
public final class TenantRequiredTables {

    private TenantRequiredTables() {}

    /** Tabela âncora: se existir, o schema tenant foi migrado (mínimo). */
    public static final String TENANT_USERS = "tenant_users";

    /** Permissões do tenant user. */
    public static final String TENANT_USER_PERMISSIONS = "tenant_user_permissions";

    /** Catálogo */
    public static final String CATEGORIES = "categories";
    public static final String SUBCATEGORIES = "subcategories";
    public static final String SUPPLIERS = "suppliers";
    public static final String PRODUCTS = "products";

    /** Vendas */
    public static final String SALES = "sales";
    public static final String SALES_ITEMS = "sales_items";

    /** “Pronto” para uso geral do tenant (âncora padrão) */
    public static String readinessAnchor() {
        return TENANT_USERS;
    }
}
