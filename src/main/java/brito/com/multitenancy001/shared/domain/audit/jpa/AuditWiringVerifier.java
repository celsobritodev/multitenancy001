package brito.com.multitenancy001.shared.domain.audit.jpa;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Verifica no startup se o AuditActorProvider foi corretamente
 * registrado no AuditActorProviders (holder estático).
 *
 * Evita cenário silencioso onde:
 * - EntityListener funciona
 * - Mas sempre retorna AuditActor.system()
 * - Porque o provider Spring não foi injetado no holder.
 */
@Component
public class AuditWiringVerifier {

    @PostConstruct
    public void verifyAuditWiring() {
        try {
            AuditActorProviders.currentOrSystem();
        } catch (Exception e) {
            throw new IllegalStateException(
                "AUDIT MISCONFIGURATION: AuditActorProvider não está registrado no AuditActorProviders. " +
                "A auditoria irá registrar tudo como SYSTEM. Verifique o bean AuditActorProvider e o método @PostConstruct.",
                e
            );
        }
    }
}

