package brito.com.multitenancy001.platform.domain.tenant;

public enum TenantAccountStatus {
    FREE_TRIAL("Trial Gratuito"),
    ACTIVE("Ativa"),
    SUSPENDED("Suspensa"),
    CANCELLED("Cancelada"),
    EXPIRED("Expirada");
    
    private final String description;
    
    TenantAccountStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Verifica se o status permite operações
     */
    public boolean isOperational() {
        return this == FREE_TRIAL || this == ACTIVE;
    }
    
    /**
     * Verifica se o status está em trial
     */
    public boolean isTrial() {
        return this == FREE_TRIAL;
    }
    
    /**
     * Verifica se o status está ativo
     */
    public boolean isActive() {
        return this == ACTIVE;
    }
    
    /**
     * Verifica se o status está suspenso
     */
    public boolean isSuspended() {
        return this == SUSPENDED;
    }
    
    /**
     * Verifica se o status está cancelado
     */
    public boolean isCancelled() {
        return this == CANCELLED || this == EXPIRED;
    }
}