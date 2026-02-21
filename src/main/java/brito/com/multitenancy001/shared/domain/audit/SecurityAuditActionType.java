package brito.com.multitenancy001.shared.domain.audit;

/**
 * Tipos de ações registradas na trilha append-only de segurança.
 *
 * Regras:
 * - NÃO remover/renomear valores (quebra histórico).
 * - SÓ adicionar novos valores, preferencialmente no final.
 *
 * Observação:
 * - Os tipos antigos permanecem válidos.
 * - Os novos tipos adicionados dão granularidade SOC2-like.
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
     */
    ACCOUNT_STATUS_CHANGED,
    
    // =========================================================
    // Accounts (append-only)
    // =========================================================

    ACCOUNT_SUSPENDED,
    ACCOUNT_RESTORED,
    

    // =========================================================
    // Billing / Payments (append-only)
    // =========================================================

    /**
     * Pagamento criado (ex.: geração de cobrança).
     */
    PAYMENT_CREATED,

    /**
     * Status do pagamento alterado (ex.: PENDING -> PAID).
     */
    PAYMENT_STATUS_CHANGED,

    /**
     * Reembolso solicitado (futuro).
     */
    PAYMENT_REFUND_REQUESTED,

    /**
     * Reembolso concluído (futuro).
     */
    PAYMENT_REFUNDED
}