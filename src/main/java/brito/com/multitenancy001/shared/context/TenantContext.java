package brito.com.multitenancy001.shared.context;

import java.util.Objects;

import brito.com.multitenancy001.shared.db.Schemas;

/**
 * Contexto de tenant por thread.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Manter o schema efetivo durante o processamento da request.</li>
 *   <li>Fornecer escopo seguro via try-with-resources.</li>
 *   <li>Bloquear rebind indevido de tenant diferente no mesmo thread.</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>PUBLIC é representado por ausência de tenant bindado.</li>
 *   <li>Bind do mesmo tenant é idempotente.</li>
 *   <li>Troca de tenant no mesmo thread gera fail-fast.</li>
 * </ul>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
        // utility class
    }

    /**
     * Retorna o tenant atual ou null.
     *
     * @return tenant atual ou null
     */
    public static String getOrNull() {
        return CURRENT.get();
    }

    /**
     * Retorna tenant atual ou PUBLIC quando não houver bind.
     *
     * @return tenant atual ou schema public
     */
    public static String getOrDefaultPublic() {
        String current = CURRENT.get();
        return current != null ? current : Schemas.CONTROL_PLANE;
    }

    /**
     * Indica se há tenant bindado.
     *
     * @return true quando houver tenant
     */
    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    /**
     * Exige tenant bindado no thread atual.
     *
     * @return tenant atual
     */
    public static String requireTenant() {
        String current = CURRENT.get();
        if (current == null) {
            throw new IllegalStateException("TenantContext ausente no thread atual");
        }
        return current;
    }

    /**
     * Abre escopo do tenant.
     *
     * @param tenantSchema schema do tenant
     * @return scope autocloseable
     */
    public static Scope scope(String tenantSchema) {
        String normalized = normalize(tenantSchema);

        if (normalized == null) {
            return publicScope();
        }

        String current = CURRENT.get();
        if (current == null) {
            bindTenantSchema(normalized);
            return new Scope(normalized, true);
        }

        if (current.equalsIgnoreCase(normalized)) {
            return new Scope(normalized, false);
        }

        throw new IllegalStateException(
                "TenantContext.bindTenantSchema: já existe TenantContext ativo. atual="
                        + current + " | novo=" + normalized
        );
    }

    /**
     * Escopo explícito em PUBLIC.
     *
     * @return scope autocloseable
     */
    public static Scope publicScope() {
        String current = CURRENT.get();
        if (current != null) {
            throw new IllegalStateException(
                    "TenantContext.bindTenantSchema: não é possível voltar para PUBLIC com TenantContext ativo. atual="
                            + current
            );
        }
        return new Scope(null, false);
    }

    /**
     * Limpa o contexto atual.
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Faz bind interno do tenant.
     *
     * @param tenantSchema schema do tenant
     */
    private static void bindTenantSchema(String tenantSchema) {
        Objects.requireNonNull(tenantSchema, "tenantSchema");
        CURRENT.set(tenantSchema);
    }

    /**
     * Normaliza valor do tenant.
     *
     * @param tenantSchema schema bruto
     * @return schema normalizado ou null
     */
    private static String normalize(String tenantSchema) {
        if (tenantSchema == null) {
            return null;
        }
        String trimmed = tenantSchema.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * Escopo autocloseable do TenantContext.
     */
    public static final class Scope implements AutoCloseable {

        private final String tenantSchema;
        private final boolean owner;
        private boolean closed;

        private Scope(String tenantSchema, boolean owner) {
            this.tenantSchema = tenantSchema;
            this.owner = owner;
        }

        /**
         * Retorna schema associado ao escopo.
         *
         * @return tenant schema
         */
        public String tenantSchema() {
            return tenantSchema;
        }

        /**
         * Informa se o escopo foi o dono do bind.
         *
         * @return true quando abriu o bind
         */
        public boolean owner() {
            return owner;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;

            if (owner) {
                TenantContext.clear();
            }
        }
    }
}