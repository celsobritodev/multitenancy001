package brito.com.multitenancy001.controlplane.domain.account;

public enum AccountStatus {
    FREE_TRIAL("Trial Gratuito"),
    ACTIVE("Ativa"),
    SUSPENDED("Suspensa"),
    CANCELLED("Cancelada"),
    EXPIRED("Expirada");
    
    private final String description;
    
    AccountStatus(String description) {
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