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

        // ✅ libera GET /api/admin/me (para o front exibir estado e mensagem)
        if ("GET".equalsIgnoreCase(method) && path.equals(ME_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        // só aplica no escopo do controlplane (/api/admin/**)
        if (!path.startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUserContext ctx) {

            if (ctx.isMustChangePassword()) {
                response.setStatus(428); // Precondition Required
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                Map<String, Object> body = Map.of(
                        "error", "MUST_CHANGE_PASSWORD",
                        "message", "Você precisa alterar a senha antes de continuar.",
                        "status", 428,
                        "details", Map.of(
                                "userId", ctx.getUserId(),
                                "username", ctx.getUsername(),
                                "accountId", ctx.getAccountId()
                        )
                );

                response.getWriter().write(objectMapper.writeValueAsString(body));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
