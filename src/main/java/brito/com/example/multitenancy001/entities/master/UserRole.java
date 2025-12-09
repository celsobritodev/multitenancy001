package brito.com.example.multitenancy001.entities.master;



public enum UserRole {
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
    
    /**
     * Verifica se o role tem permissões administrativas
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }
    
    /**
     * Verifica se o role pode gerenciar usuários
     */
    public boolean canManageUsers() {
        return this == ADMIN || this == SUPPORT;
    }
    
    /**
     * Verifica se o role pode gerenciar produtos
     */
    public boolean canManageProducts() {
        return this == ADMIN || this == PRODUCT_MANAGER;
    }
    
    /**
     * Verifica se o role pode gerenciar vendas
     */
    public boolean canManageSales() {
        return this == ADMIN || this == SALES_MANAGER || this == FINANCEIRO;
    }
    
    /**
     * Verifica se o role tem acesso a relatórios
     */
    public boolean canViewReports() {
        return this == ADMIN || this == PRODUCT_MANAGER || 
               this == SALES_MANAGER || this == FINANCEIRO || this == VIEWER;
    }
}