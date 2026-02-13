package brito.com.multitenancy001.shared.security;

/**
 * Contrato mínimo para Principal autenticado.
 *
 * Evita acoplamento do shared/persistence com classes de infraestrutura.
 */
public interface AuthenticatedPrincipal {
    Long getUserId();
    String getEmail();
}


