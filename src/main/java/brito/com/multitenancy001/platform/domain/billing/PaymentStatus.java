package brito.com.multitenancy001.platform.domain.billing;



public enum PaymentStatus {
    PENDING("Pendente"),
    COMPLETED("Concluído"),
    FAILED("Falhou"),
    REFUNDED("Reembolsado"),
    CANCELLED("Cancelado"),
    EXPIRED("Expirado");
    
    private final String description;
    
    PaymentStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Verifica se o status indica pagamento bem-sucedido
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
    
    /**
     * Verifica se o status indica pagamento finalizado (não pendente)
     */
    public boolean isFinal() {
        return this != PENDING;
    }
    
    /**
     * Converte string para PaymentStatus
     */
    public static PaymentStatus fromString(String status) {
        if (status == null) {
            return PENDING;
        }
        try {
            return PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}