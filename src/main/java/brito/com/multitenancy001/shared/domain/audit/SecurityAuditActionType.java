package brito.com.multitenancy001.shared.domain.audit;

/**
 * Tipos de ações registradas na trilha append-only de segurança.
 *
 * Regras:
 * - NÃO remover/renomear valores (quebra histórico).
 * - Só adicionar novos valores.
 *
 * Observação:
 * - Os tipos antigos (USER_CREATED etc) permanecem válidos.
 * - Os novos tipos adicionados dão granularidade SOC2-like (ex.: delete, ownership transfer, separação por contexto).
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
    PERMISSIONS_CHANGED,

    // =========================================================
    // SOC2-like additions (append-only)
    // =========================================================

    /**
     * Usuário removido logicamente (soft delete).
     */
    USER_SOFT_DELETED,

    /**
     * Usuário restaurado após soft delete.
     * (Diferente de USER_RESTORED, que costuma ser "unsuspend/reenable".)
     */
    USER_SOFT_RESTORED,

    /**
     * Transferência de ownership (ex.: dono do tenant/conta).
     */
    OWNERSHIP_TRANSFERRED,

    /**
     * Mudança de status administrativa em Account/Tenant (suspend/resume).
     * Útil quando você quer auditar o ato administrativo além do efeito em users.
     */
    ACCOUNT_STATUS_CHANGED
}