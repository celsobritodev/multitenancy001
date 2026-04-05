package brito.com.multitenancy001.shared.context;

import java.util.Objects;

import brito.com.multitenancy001.shared.db.Schemas;

/**
 * Contexto de tenant por thread (ponto de acesso central para multi-tenancy).
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Manter o identificador do schema ativo (tenantSchema) durante o processamento da requisição.</li>
 *   <li>Fornecer um mecanismo seguro de escopo ({@link Scope}) via try-with-resources.</li>
 *   <li>Impedir a troca indevida de tenant no mesmo thread (fail-fast).</li>
 * </ul>
 *
 * <p><b>Convenções:</b></p>
 * <ul>
 *   <li>A ausência de um tenant bindado representa o schema PUBLIC (Control Plane).</li>
 *   <li>O bind do mesmo tenant é idempotente e não causa erro.</li>
 *   <li>A tentativa de bind de um tenant diferente quando outro já está ativo lança {@link IllegalStateException}.</li>
 * </ul>
 *
 * <p><b>Uso típico:</b></p>
 * <pre>{@code
 * // Executar um bloco no contexto de um tenant específico
 * try (TenantContext.Scope scope = TenantContext.scope(tenantSchema)) {
 *     // Qualquer operação de banco de dados aqui será roteada para o schema 'tenantSchema'
 * }
 * }</pre>
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