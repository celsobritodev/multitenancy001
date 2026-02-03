package brito.com.multitenancy001.infrastructure.security.filter;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        long threadId = Thread.currentThread().threadId();
        String method = req.getMethod();
        String uri = req.getRequestURI();

        try {
            chain.doFilter(req, res);
        } finally {
            String tenant = TenantContext.getOrNull();
            String tenantForLog = (tenant == null ? "PUBLIC" : tenant);

            RequestMeta meta = RequestMetaContext.getOrNull();
            String requestId = (meta != null && meta.requestId() != null) ? meta.requestId().toString() : "-";
            String ip = (meta != null && meta.ip() != null) ? meta.ip() : "-";

            log.info("🌐 [REQ] {} {} | tenant={} | status={} | ip={} | requestId={} | thread={}",
                    method, uri, tenantForLog, res.getStatus(), ip, requestId, threadId);
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
