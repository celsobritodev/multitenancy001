package brito.com.multitenancy001.infrastructure.persistence.multitenancy.hibernate;

import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolver do schema atual para integração com Hibernate multi-tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver o schema efetivo a partir do {@link TenantContext}.</li>
 *   <li>Fornecer helpers estáticos legados para leitura/limpeza do contexto.</li>
 *   <li>Manter schema PUBLIC como fallback padrão quando não houver tenant bindado.</li>
 * </ul>
 *
 * <p>Observação importante:</p>
 * <ul>
 *   <li>O bind explícito de tenant deve ocorrer preferencialmente via {@link TenantContext#scope(String)}.</li>
 *   <li>Este resolver não deve violar o encapsulamento interno do {@link TenantContext}.</li>
 * </ul>
 */
@Slf4j
@Component
public class TenantSchemaResolver implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    /**
     * Método legado mantido por compatibilidade.
     *
     * <p>Não faz bind direto no thread porque o novo hardening centraliza isso
     * em {@link TenantContext#scope(String)}. Este método agora falha rápido
     * para evitar uso incorreto fora de escopo controlado.</p>
     *
     * @param tenantSchema schema do tenant
     */
    public static void bindSchemaToCurrentThread(String tenantSchema) {
        throw new UnsupportedOperationException(
                "Use TenantContext.scope(tenantSchema) com try-with-resources em vez de bindSchemaToCurrentThread"
        );
    }

    /**
     * Retorna schema bindado ou null.
     *
     * @return schema atual ou null
     */
    public static String resolveBoundSchemaOrNull() {
        return TenantContext.getOrNull();
    }

    /**
     * Retorna schema bindado ou PUBLIC como fallback.
     *
     * @return schema atual ou PUBLIC
     */
    public static String resolveBoundSchemaOrDefault() {
        String tenantSchema = TenantContext.getOrNull();
        return (tenantSchema != null ? tenantSchema : DEFAULT_SCHEMA);
    }

    /**
     * Limpa o contexto do thread atual.
     */
    public static void unbindSchemaFromCurrentThread() {
        TenantContext.clear();
    }

    /**
     * Resolve schema atual para o Hibernate.
     *
     * @return identificador do tenant/schema efetivo
     */
    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantSchema = TenantContext.getOrNull();
        String resolved = (StringUtils.hasText(tenantSchema) ? tenantSchema : DEFAULT_SCHEMA);

        if (log.isDebugEnabled()) {
            log.debug("🏷️ Hibernate resolveu schema={} (bound={}, default={})",
                    resolved, tenantSchema, DEFAULT_SCHEMA);
        }

        return resolved;
    }

    /**
     * Indica se sessões correntes devem ser revalidadas.
     *
     * @return false
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    /**
     * Indica se o tenant informado é o schema root/public.
     *
     * @param tenantIdentifier identificador do tenant
     * @return true quando for schema root/public
     */
    @Override
    public boolean isRoot(String tenantIdentifier) {
        return DEFAULT_SCHEMA.equals(tenantIdentifier);
    }
}