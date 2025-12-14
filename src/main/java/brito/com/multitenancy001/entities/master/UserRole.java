package brito.com.multitenancy001.entities.master;

public enum UserRole {

    // 🌐 Plataforma
    SUPER_ADMIN("Super Administrador da Plataforma"),

    // 🏢 Tenant
    ADMIN("Administrador"),
    PRODUCT_MANAGER("Gerente de Produtos"),
    SALES_MANAGER("Gerente de Vendas"),
    VIEWER("Visualizador"),
    SUPPORT("Suporte"),
    FINANCEIRO("Financeiro"),
    OPERACOES("Operações");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /* ======================================================
       CONTEXTO DA ROLE
       ====================================================== */

    /**
     * Verifica se é um papel de plataforma (fora do tenant)
     */
    public boolean isPlatformRole() {
        return this == SUPER_ADMIN;
    }

    /**
     * Verifica se é um papel de tenant
     */
    public boolean isTenantRole() {
        return this != SUPER_ADMIN;
    }

    /* ======================================================
       PERMISSÕES ADMINISTRATIVAS
       ====================================================== */

    /**
     * Verifica se o role tem permissões administrativas
     * SUPER_ADMIN e ADMIN
     */
    public boolean isAdmin() {
        return this == SUPER_ADMIN || this == ADMIN;
    }

    /**
     * Verifica se pode gerenciar usuários
     */
    public boolean canManageUsers() {
        return this == SUPER_ADMIN || this == ADMIN || this == SUPPORT;
    }

    /**
     * Verifica se pode gerenciar produtos
     */
    public boolean canManageProducts() {
        return this == SUPER_ADMIN || this == ADMIN || this == PRODUCT_MANAGER;
    }

    /**
     * Verifica se pode gerenciar vendas
     */
    public boolean canManageSales() {
        return this == SUPER_ADMIN || this == ADMIN
            || this == SALES_MANAGER || this == FINANCEIRO;
    }

    /**
     * Verifica se tem acesso a relatórios
     */
    public boolean canViewReports() {
        return this == SUPER_ADMIN || this == ADMIN
            || this == PRODUCT_MANAGER
            || this == SALES_MANAGER
            || this == FINANCEIRO
            || this == VIEWER;
    }

    /* ======================================================
       SEGURANÇA / CONVENIÊNCIA
       ====================================================== */

    /**
     * Retorna o nome da role no padrão Spring Security
     * Ex: ROLE_SUPER_ADMIN
     */
    public String asAuthority() {
        return "ROLE_" + this.name();
    }
}
