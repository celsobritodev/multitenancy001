package brito.com.multitenancy001.shared.security;

/**
 * Contrato "shared" para permissões.
 *
 * - O shared pode depender deste contrato.
 * - ControlPlane e Tenant podem implementar este contrato nos seus enums.
 * - O shared NÃO deve importar classes/enums dos bounded contexts.
 *
 * Observação:
 * - Mantemos compatibilidade com o seu modelo atual (PermissionAuthority),
 *   já que o resto do projeto usa "asAuthority()" para gerar GrantedAuthority/JWT claims.
 */
public interface PermissionCode extends PermissionAuthority {
    // marker + compatibilidade (herda asAuthority())
}

