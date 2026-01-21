package brito.com.multitenancy001.shared.db;

public final class Schemas {

    /**
     * Schema do Control Plane (tabelas globais).
     * Hoje é Schemas.CONTROL_PLANE (Postgres default).
     */
    public static final String CONTROL_PLANE = "public";

    private Schemas() {}
}
