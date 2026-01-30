package brito.com.multitenancy001.shared.db;

public final class Schemas {

    /**
     * Schema do Control Plane (tabelas globais).
     * É o schema fallback (root) quando não há tenant bindado.
     */
    public static final String CONTROL_PLANE = "public";

    private Schemas() {}
}
