package brito.com.multitenancy001.infrastructure.security.filter;

import brito.com.multitenancy001.shared.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro responsável por aplicar binding de tenant via header em fluxos sem Bearer
 * e por forçar PUBLIC em rotas que precisam necessariamente rodar fora de tenant.
 *
 * <p>Regra desta versão endurecida:</p>
 * <ul>
 *   <li>Rotas public-only sempre executam em {@code PUBLIC}.</li>
 *   <li>Requests com Bearer não fazem bind por header neste filtro.</li>
 *   <li>Requests sem Bearer podem usar {@code X-Tenant} para bind antecipado.</li>
 * </ul>
 *
 * <p>Essa separação evita competição de contexto entre filtro de header e filtro JWT.</p>
 */
@Slf4j
public class TenantHeaderTenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final long threadId = Thread.currentThread().threadId();
        final String method = request.getMethod();
        final String uri = request.getRequestURI();

        if (isPublicOnlyPath(uri)) {
            try (TenantContext.Scope ignored = TenantContext.publicScope()) {
                log.info("🌐 [REQ] {} {} | context=PUBLIC_ONLY | thread={}", method, uri, threadId);
                filterChain.doFilter(request, response);
            }
            return;
        }

        final String rawHeader = request.getHeader(TENANT_HEADER);
        final String tenantHeader = normalize(rawHeader);
        final String tenantForLog = tenantHeader != null ? tenantHeader : "PUBLIC";

        final String authHeader = request.getHeader("Authorization");
        final boolean hasBearer = StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ");

        if (hasBearer) {
            if (tenantHeader != null && !isValidTenantSchema(tenantHeader)) {
                log.warn("🚫 X-Tenant inválido recebido com Bearer | uri={} | xTenant={}", uri, tenantHeader);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Tenant header");
                return;
            }

            log.info("🌐 [REQ] {} {} | X-Tenant(header)={} | bearer=yes | sem bind por header | thread={}",
                    method, uri, tenantForLog, threadId);
            filterChain.doFilter(request, response);
            return;
        }

        if (tenantHeader != null && !isValidTenantSchema(tenantHeader)) {
            log.warn("🚫 X-Tenant inválido sem Bearer | uri={} | xTenant={}", uri, tenantHeader);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Tenant header");
            return;
        }

        try (TenantContext.Scope ignored = TenantContext.scope(tenantHeader)) {
            log.info("🌐 [REQ] {} {} | X-Tenant(bound)={} | bearer=no | thread={}",
                    method, uri, tenantForLog, threadId);
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Define rotas que obrigatoriamente devem executar em PUBLIC.
     *
     * @param uri URI da request
     * @return true se a rota for public-only
     */
    private static boolean isPublicOnlyPath(String uri) {
        return uri.startsWith("/api/tenant/auth/")
                || uri.startsWith("/api/controlplane/auth/")
                || uri.startsWith("/api/tenant/password/")
                || uri.startsWith("/api/signup");
    }

    /**
     * Valida formato do tenant schema recebido em header.
     *
     * @param tenantSchema tenant schema
     * @return true se o formato for aceitável
     */
    private boolean isValidTenantSchema(String tenantSchema) {
        return StringUtils.hasText(tenantSchema) && tenantSchema.matches("^[a-zA-Z0-9_]+$");
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/favicon.ico");
    }
}