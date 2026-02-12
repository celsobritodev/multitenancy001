package brito.com.multitenancy001.shared.domain.audit;

/**
 * Tipos de ações registradas na trilha append-only de segurança.
 *
 * Regras:
 * - NÃO remover/renomear valores (quebra histórico).
 * - Só adicionar novos valores.
 */
public enum SecurityAuditActionType {

    // =========================================================
    // Password flows
    // =========================================================

    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,

    /**
     * Troca de senha autenticada (senha atual + nova senha).
     * (Não é reset via token.)
     */
    PASSWORD_CHANGED,

    // =========================================================
    // User administration (CP / Tenant)
    // =========================================================

    USER_CREATED,
    USER_UPDATED,
    USER_SUSPENDED,
    USER_RESTORED,
    ROLE_CHANGED,
    PERMISSIONS_CHANGED
}
