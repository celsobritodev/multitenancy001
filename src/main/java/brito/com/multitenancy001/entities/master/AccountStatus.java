package brito.com.multitenancy001.entities.master;



public enum AccountStatus {
    FREE_TRIAL,
    ACTIVE,
    SUSPENDED,
    CANCELLED,
    PENDING_PAYMENT;
    
    /**
     * Verifica se o status indica uma conta ativa
     */
    public boolean isActive() {
        return this == ACTIVE || this == FREE_TRIAL;
    }
    
    /**
     * Verifica se o status indica que a conta está suspensa
     */
    public boolean isSuspended() {
        return this == SUSPENDED || this == PENDING_PAYMENT;
    }
    
    /**
     * Verifica se o status indica que a conta está cancelada
     */
    public boolean isCancelled() {
        return this == CANCELLED;
    }
    
    /**
     * Obtém a descrição do status
     */
    public String getDescription() {
        return switch (this) {
            case FREE_TRIAL -> "Período de Teste Gratuito";
            case ACTIVE -> "Ativa";
            case SUSPENDED -> "Suspensa";
            case CANCELLED -> "Cancelada";
            case PENDING_PAYMENT -> "Pagamento Pendente";
        };
    }
}