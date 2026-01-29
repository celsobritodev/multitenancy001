package brito.com.multitenancy001.controlplane.domain.account;

public enum AccountStatus {
    FREE_TRIAL("Trial Gratuito"),
    ACTIVE("Ativa"),
    SUSPENDED("Suspensa"),
    CANCELLED("Cancelada"),
    EXPIRED("Expirada"),
	PROVISIONING("Provisionamento");

    private final String description;

    AccountStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Regra de negócio: permite operar (independente de datas finas do trial). */
    public boolean isOperational() {
        return this == FREE_TRIAL || this == ACTIVE;
    }

    public boolean isTrial() {
        return this == FREE_TRIAL;
    }

    public boolean isSuspended() {
        return this == SUSPENDED;
    }

    public boolean isCancelled() {
        return this == CANCELLED || this == EXPIRED;
    }

 
}
