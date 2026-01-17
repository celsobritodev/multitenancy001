package brito.com.multitenancy001.controlplane.domain.account;

public enum AccountOrigin {
    BUILT_IN,   // seed / migration
    ADMIN,      // criado via painel por superadmin
    API         // futuro (integração, signup automático, etc)
}
