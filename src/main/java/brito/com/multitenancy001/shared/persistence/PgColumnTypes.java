package brito.com.multitenancy001.shared.persistence;

/**
 * Constantes de tipos Postgres para usar em @Column(columnDefinition = ...).
 * Evita typo e padroniza o projeto.
 */
public final class PgColumnTypes {

    public static final String TIMESTAMPTZ = "timestamptz";
    public static final String DATE = "date";
    public static final String JSONB = "jsonb";
    public static final String INET = "inet";
    public static final String TEXT = "TEXT";

    private PgColumnTypes() {
        // utility class
    }
}
