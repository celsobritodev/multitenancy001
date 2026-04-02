package brito.com.multitenancy001.infrastructure.security.filter;

import brito.com.multitenancy001.shared.context.RequestMeta;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro responsável por inicializar e limpar metadados de request no contexto local do thread.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Gerar ou propagar {@code X-Request-Id}.</li>
 *   <li>Popular {@link RequestMetaContext} com requestId, método, URI, IP e user-agent.</li>
 *   <li>Garantir limpeza defensiva de {@link RequestMetaContext} e {@link TenantContext} ao final.</li>
 * </ul>
 *
 * <p>Observação importante:</p>
 * <ul>
 *   <li>Este filtro é o responsável pela limpeza final dos contextos por request.</li>
 *   <li>Os demais filtros podem abrir escopos, mas não devem assumir limpeza global fora do seu try/finally.</li>
 * </ul>
 */
@Slf4j
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

        if (log.isDebugEnabled()) {
            log.debug("🧾 RequestMetaContext set | requestId={} | method={} | uri={} | ip={}",
                    meta.requestId(),
                    meta.method(),
                    meta.uri(),
                    meta.ip());
        }

        try {
            chain.doFilter(req, res);
        } finally {
            try {
                TenantContext.clear();
            } catch (Exception ex) {
                log.warn("⚠️ Falha ao limpar TenantContext no finally do RequestMetaContextFilter: {}",
                        ex.getMessage());
            }

            try {
                RequestMetaContext.clear();
            } catch (Exception ex) {
                log.warn("⚠️ Falha ao limpar RequestMetaContext no finally: {}",
                        ex.getMessage());
            }
        }
    }

    /**
     * Resolve requestId a partir do header ou gera um novo UUID.
     *
     * @param req request atual
     * @return UUID do request
     */
    private UUID resolveRequestId(HttpServletRequest req) {
        String raw = req.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(raw)) {
            try {
                return UUID.fromString(raw.trim());
            } catch (Exception ignored) {
                log.debug("X-Request-Id inválido recebido; será gerado novo UUID");
            }
        }
        return UUID.randomUUID();
    }

    /**
     * Resolve IP do cliente.
     *
     * @param req request atual
     * @return IP do cliente
     */
    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(xff)) {
            String first = xff.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        return req.getRemoteAddr();
    }

    /**
     * Resolve user-agent da request.
     *
     * @param req request atual
     * @return user-agent ou null
     */
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