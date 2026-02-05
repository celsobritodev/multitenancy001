package brito.com.multitenancy001.shared.domain.audit;

public record AuditActor(Long userId, String email) {

    public static AuditActor system() {
        return new AuditActor(null, "system");
    }
}
