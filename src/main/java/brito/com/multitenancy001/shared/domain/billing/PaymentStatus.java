package brito.com.multitenancy001.shared.domain.billing;

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

    public boolean isSuccessful() {
        return this == COMPLETED;
    }

    public boolean isFinal() {
        return this != PENDING;
    }

    public static PaymentStatus fromString(String status) {
        if (status == null) return PENDING;
        try {
            return PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
