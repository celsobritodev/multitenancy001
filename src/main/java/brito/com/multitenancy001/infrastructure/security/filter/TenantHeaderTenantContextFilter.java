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
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {

    final long threadId = Thread.currentThread().threadId();
    final String method = request.getMethod();
    final String uri = request.getRequestURI();

    // ✅ Padrão B: auth/signup SEMPRE rodam em PUBLIC (não binda tenant por header)
    if (isPublicOnlyPath(uri)) {
        try (TenantContext.Scope ignored = TenantContext.publicScope()) {
            log.info("🌐 [REQ] {} {} | PUBLIC-ONLY | thread={}", method, uri, threadId);
            filterChain.doFilter(request, response);
        }
        return;
    }

    final String rawHeader = request.getHeader(TENANT_HEADER);
    final String tenantHeader = (rawHeader == null ? null : rawHeader.trim());
    final String tenantSchemaFromHeader = StringUtils.hasText(tenantHeader) ? tenantHeader : null;
    final String tenantForLog = (tenantSchemaFromHeader != null ? tenantSchemaFromHeader : "PUBLIC");

    // ✅ Se tem Bearer, não binda por header (token manda)
    final String authHeader = request.getHeader("Authorization");
    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
        log.info("🌐 [REQ] {} {} | X-Tenant(header)={} | bearer=yes | thread={}",
                method, uri, tenantForLog, threadId);
        filterChain.doFilter(request, response);
        return;
    }

    // ✅ Sem Bearer -> binda por header (exceto rotas public-only)
    try (TenantContext.Scope ignored = TenantContext.scope(tenantSchemaFromHeader)) {
        log.info("🌐 [REQ] {} {} | X-Tenant(bound)={} | bearer=no | thread={}",
                method, uri, tenantForLog, threadId);
        filterChain.doFilter(request, response);
    }
}

private static boolean isPublicOnlyPath(String uri) {
    // auth de tenant e control-plane + signup devem rodar em PUBLIC
    return uri.startsWith("/api/tenant/auth/")
            || uri.startsWith("/api/controlplane/auth/")
            || uri.startsWith("/api/signup");
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
