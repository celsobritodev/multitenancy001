package brito.com.multitenancy001.shared.domain.common;

/**
 * Origem de criação de entidades administrativas (seed, painel, integrações).
 *
 * Mantida em shared para evitar duplicação e vazamento de boundary
 * (controlplane <-> tenant).
 */
public enum EntityOrigin {
    BUILT_IN,
    ADMIN,
    API
}
