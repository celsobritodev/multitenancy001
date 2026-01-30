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

        final String raw = request.getHeader(TENANT_HEADER);
        final String tenantHeader = (raw == null ? null : raw.trim());
        final String tenantForLog = StringUtils.hasText(tenantHeader) ? tenantHeader : "PUBLIC";

        // ✅ bind no começo; restaura o anterior ao sair do try
        try (TenantContext.Scope ignored = TenantContext.scope(tenantHeader)) {

            // ✅ 1 linha por request (limpa)
            log.info("🌐 [REQ] {} {} | X-Tenant={} | thread={}",
                    method, uri, tenantForLog, threadId);

            filterChain.doFilter(request, response);

        } finally {
            // ✅ HARD RESET: garante que a thread termina PUBLIC (sem tenant)
            // Perfeito pra debug e evita "vazamento" de tenant em reuso de thread.
            try {
                TenantContext.clear();
            } catch (Exception ignore) {
                // no-op (debug hardening)
            }
        }
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
