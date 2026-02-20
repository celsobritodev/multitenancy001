package brito.com.multitenancy001.tenant.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

import static brito.com.multitenancy001.tenant.security.TenantPermission.*;

/**
 * Centraliza a matriz Role -> Permissions do Tenant.
 *
 * Regras:
 * - sempre devolve Set imutável
 * - toda role deve estar mapeada explicitamente
 * - FAIL-FAST: role sem mapeamento explode na inicialização e/ou no uso
 * - FAIL-FAST: role decorativa (set vazio) é bug
 * - FAIL-FAST: permissão duplicada no mapeamento é bug (evita erro humano escondido por Set)
 */
public final class TenantRolePermissions {

    private static final EnumMap<TenantRole, Set<TenantPermission>> MAP = new EnumMap<>(TenantRole.class);

    static {
        // OWNER = tudo
        MAP.put(TenantRole.TENANT_OWNER, unmodifiable(EnumSet.allOf(TenantPermission.class)));

        // ADMIN = "admin total" do tenant
        MAP.put(TenantRole.TENANT_ADMIN, unmodifiable(strict(
                // Users
                TEN_USER_READ,
                TEN_USER_CREATE,
                TEN_USER_UPDATE,
                TEN_USER_SUSPEND,
                TEN_USER_RESTORE,
                TEN_USER_DELETE,

                // Transfer ownership/admin
                TEN_ROLE_TRANSFER,

                // Products + Inventory
                TEN_PRODUCT_READ,
                TEN_PRODUCT_WRITE,
                TEN_INVENTORY_READ,
                TEN_INVENTORY_WRITE,

                // Catalog
                TEN_CATEGORY_READ,
                TEN_CATEGORY_WRITE,
                TEN_SUPPLIER_READ,
                TEN_SUPPLIER_WRITE,

                // Sales + Issues + Reports
                TEN_SALE_READ,
                TEN_SALE_WRITE,
                TEN_SALE_ISSUES_READ,
                TEN_SALE_ISSUES_WRITE,
                TEN_REPORT_SALES_READ,

                // Billing + Settings
                TEN_BILLING_READ,
                TEN_BILLING_WRITE,
                TEN_SETTINGS_READ,
                TEN_SETTINGS_WRITE
        )));

        /**
         * MANAGER = "admin operacional" (sem poderes sensíveis/destrutivos).
         */
        MAP.put(TenantRole.TENANT_MANAGER, unmodifiable(strict(
                // Users (sem delete)
                TEN_USER_READ,
                TEN_USER_CREATE,
                TEN_USER_UPDATE,
                TEN_USER_SUSPEND,
                TEN_USER_RESTORE,

                // Products + Inventory
                TEN_PRODUCT_READ,
                TEN_PRODUCT_WRITE,
                TEN_INVENTORY_READ,
                TEN_INVENTORY_WRITE,

                // Catalog
                TEN_CATEGORY_READ,
                TEN_CATEGORY_WRITE,
                TEN_SUPPLIER_READ,
                TEN_SUPPLIER_WRITE,

                // Sales + Issues + Reports
                TEN_SALE_READ,
                TEN_SALE_WRITE,
                TEN_SALE_ISSUES_READ,
                TEN_SALE_ISSUES_WRITE,
                TEN_REPORT_SALES_READ,

                // Billing + Settings (read-only)
                TEN_BILLING_READ,
                TEN_SETTINGS_READ
        )));

        MAP.put(TenantRole.TENANT_PRODUCT_MANAGER, unmodifiable(strict(
                TEN_PRODUCT_READ,
                TEN_PRODUCT_WRITE,
                TEN_INVENTORY_READ,
                TEN_INVENTORY_WRITE
        )));

        MAP.put(TenantRole.TENANT_SALES_MANAGER, unmodifiable(strict(
                TEN_SALE_READ,
                TEN_SALE_WRITE,
                TEN_SALE_ISSUES_READ,
                TEN_SALE_ISSUES_WRITE,
                TEN_REPORT_SALES_READ
        )));

        MAP.put(TenantRole.TENANT_BILLING_MANAGER, unmodifiable(strict(
                TEN_BILLING_READ,
                TEN_BILLING_WRITE
        )));

        // READ_ONLY = auditor/consulta (sem write)
        MAP.put(TenantRole.TENANT_READ_ONLY, unmodifiable(strict(
                TEN_PRODUCT_READ,
                TEN_INVENTORY_READ,
                TEN_CATEGORY_READ,
                TEN_SUPPLIER_READ,
                TEN_SALE_READ,
                TEN_SALE_ISSUES_READ,
                TEN_REPORT_SALES_READ,
                TEN_USER_READ,
                TEN_BILLING_READ,
                TEN_SETTINGS_READ
        )));

        // USER = usuário comum
        MAP.put(TenantRole.TENANT_USER, unmodifiable(strict(
                TEN_PRODUCT_READ,
                TEN_CATEGORY_READ,
                TEN_SUPPLIER_READ,
                TEN_INVENTORY_READ,
                TEN_SALE_READ,
                TEN_SALE_WRITE
        )));

        // OPERATOR operacional
        MAP.put(TenantRole.TENANT_OPERATOR, unmodifiable(strict(
                TEN_PRODUCT_READ,
                TEN_INVENTORY_READ,
                TEN_INVENTORY_WRITE,
                TEN_SALE_READ
        )));

        // SUPPORT (tenant)
        MAP.put(TenantRole.TENANT_SUPPORT, unmodifiable(strict(
                TEN_USER_READ,
                TEN_USER_UPDATE,
                TEN_USER_SUSPEND,
                TEN_USER_RESTORE,

                TEN_PRODUCT_READ,
                TEN_INVENTORY_READ,

                TEN_SALE_READ,
                TEN_SALE_ISSUES_READ,

                TEN_SETTINGS_READ,
                TEN_BILLING_READ
        )));

        // FAIL-FAST: garante que todas as roles do enum estão mapeadas e não vazias.
        assertAllRolesMappedAndNonEmpty();
    }

    private TenantRolePermissions() {}

    /**
     * Obtém permissões base de uma role do Tenant.
     *
     * @param role role do tenant (obrigatória)
     * @return set imutável de permissões
     */
    public static Set<TenantPermission> permissionsFor(TenantRole role) {
        // método: valida parâmetros e devolve set imutável
        if (role == null) {
            throw new IllegalArgumentException("Role do Tenant é obrigatória (null)");
        }

        Set<TenantPermission> set = MAP.get(role);
        if (set == null) {
            throw new IllegalStateException("Role do Tenant sem mapeamento em TenantRolePermissions: " + role);
        }
        if (set.isEmpty()) {
            throw new IllegalStateException("Role do Tenant com set de permissões vazio (role decorativa): " + role);
        }
        return set;
    }

    private static Set<TenantPermission> unmodifiable(EnumSet<TenantPermission> set) {
        return Collections.unmodifiableSet(set);
    }

    /**
     * Constrói um EnumSet com fail-fast para duplicatas.
     */
    @SafeVarargs
    private static EnumSet<TenantPermission> strict(TenantPermission... perms) {
        // método: monta set e explode se houver duplicata por erro humano
        EnumSet<TenantPermission> set = EnumSet.noneOf(TenantPermission.class);
        for (TenantPermission p : perms) {
            if (p == null) continue;
            if (!set.add(p)) {
                throw new IllegalStateException("Permissão duplicada no mapeamento TenantRolePermissions: " + p.name());
            }
        }
        return set;
    }

    private static void assertAllRolesMappedAndNonEmpty() {
        for (TenantRole role : TenantRole.values()) {
            Set<TenantPermission> perms = MAP.get(role);
            if (perms == null) {
                throw new IllegalStateException("Role do Tenant sem mapeamento em TenantRolePermissions: " + role);
            }
            if (perms.isEmpty()) {
                throw new IllegalStateException("Role do Tenant com set de permissões vazio (role decorativa): " + role);
            }
        }
    }
}