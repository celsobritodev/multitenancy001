package brito.com.multitenancy001.infrastructure.security.filter;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_PATH = "/api/admin/me/password";
    private static final String ME_PATH              = "/api/admin/me";

    private static final String LOGIN_PATH   = "/api/admin/auth/login";
    private static final String REFRESH_PATH = "/api/admin/auth/refresh";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod();
        String path = request.getRequestURI();

        // ✅ libera preflight (CORS)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ libera login/refresh
        if (path.startsWith(LOGIN_PATH) || path.startsWith(REFRESH_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ libera troca de senha
        if (path.startsWith(CHANGE_PASSWORD_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUserContext ctx)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ libera /me (pra UI conseguir saber que precisa trocar senha)
        if (path.startsWith(ME_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (ctx.isMustChangePassword()) {
            response.setStatus(428);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> body = Map.of(
                    "error", "MUST_CHANGE_PASSWORD",
                    "message", "Você precisa alterar a senha antes de continuar.",
                    "status", 428,
                    "details", Map.of(
                            "userId", ctx.getUserId(),
                            "email", ctx.getEmail(),
                            "accountId", ctx.getAccountId()
                    )
            );

            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        filterChain.doFilter(request, response);
    }
}

