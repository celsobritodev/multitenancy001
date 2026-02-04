package brito.com.multitenancy001.infrastructure.security.filter;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class RequestMetaContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        RequestMeta meta = new RequestMeta(
                resolveRequestId(req),
                req.getMethod(),
                req.getRequestURI(),
                resolveClientIp(req),
                resolveUserAgent(req)
        );

        RequestMetaContext.set(meta);

        if (meta.requestId() != null) {
            res.setHeader(REQUEST_ID_HEADER, meta.requestId().toString());
        }

        try {
            chain.doFilter(req, res);
        } finally {
            // ✅ limpeza centralizada
            try { TenantContext.clear(); } catch (Exception ignore) {}
            RequestMetaContext.clear();
        }
    }

    private UUID resolveRequestId(HttpServletRequest req) {
        String raw = req.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(raw)) {
            try { return UUID.fromString(raw.trim()); } catch (Exception ignore) {}
        }
        return UUID.randomUUID();
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(xff)) {
            String first = xff.split(",")[0].trim();
            if (StringUtils.hasText(first)) return first;
        }
        return req.getRemoteAddr();
    }

    private String resolveUserAgent(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return StringUtils.hasText(ua) ? ua.trim() : null;
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

