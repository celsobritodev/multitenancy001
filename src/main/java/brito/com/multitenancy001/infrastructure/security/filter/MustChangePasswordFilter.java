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

    // =========================
    // ADMIN
    // =========================
    private static final String ADMIN_CHANGE_PASSWORD_PATH = "/api/admin/me/password";
    private static final String ADMIN_ME_PATH              = "/api/admin/me";
    private static final String ADMIN_LOGIN_PATH           = "/api/admin/auth/login";
    private static final String ADMIN_REFRESH_PATH         = "/api/admin/auth/refresh";

    // =========================
    // TENANT
    // =========================
    private static final String TENANT_CHANGE_PASSWORD_PATH = "/api/tenant/me/password";
    private static final String TENANT_LOGIN_PATH           = "/api/tenant/auth/login";
    private static final String TENANT_LOGIN_CONFIRM_PATH   = "/api/tenant/auth/login/confirm";
    private static final String TENANT_REFRESH_PATH         = "/api/tenant/auth/refresh";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String method = request.getMethod();
        String path = request.getRequestURI();

        // ✅ libera preflight (CORS)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ libera login/refresh (admin + tenant)
        if (path.startsWith(ADMIN_LOGIN_PATH)
                || path.startsWith(ADMIN_REFRESH_PATH)
                || path.startsWith(TENANT_LOGIN_PATH)
                || path.startsWith(TENANT_LOGIN_CONFIRM_PATH)
                || path.startsWith(TENANT_REFRESH_PATH)
        ) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ libera troca de senha (admin + tenant)
        if (path.startsWith(ADMIN_CHANGE_PASSWORD_PATH) || path.startsWith(TENANT_CHANGE_PASSWORD_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUserContext ctx)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ (mantém seu comportamento atual) libera /api/admin/me
        // (tenant /api/tenant/me continua retornando 428 enquanto mustChangePassword=true,
        //  que é exatamente o que você está vendo hoje.)
        if (path.startsWith(ADMIN_ME_PATH)) {
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
