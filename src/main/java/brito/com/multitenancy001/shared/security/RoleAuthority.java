package brito.com.multitenancy001.shared.security;

/**
 * Contrato comum para roles da plataforma e do tenant
 * Permite unificação no Spring Security (GrantedAuthority)
 */
public interface RoleAuthority {

    /**
     * Retorna a authority no padrão Spring Security
     *
     */
    String asAuthority();

    /**
     * Helper padrão (opcional)
     */
    default boolean isAdmin() {
        return false;
    }
}
