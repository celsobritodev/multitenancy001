package brito.com.multitenancy001.shared.persistence.publicschema;

/**
 * Tipos de subject para a tabela login_identities.
 */
public enum LoginIdentitySubjectType {

    TENANT_ACCOUNT,
    CONTROLPLANE_USER;

    public String getDbValue() {
        return this.name();
    }
}