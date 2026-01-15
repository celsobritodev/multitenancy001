package brito.com.multitenancy001.infrastructure.security.filter;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.security.userdetails.MultiContextUserDetailsService;
import brito.com.multitenancy001.shared.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final MultiContextUserDetailsService multiContextUserDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest httpServletRequest,
            @NonNull HttpServletResponse httpServletResponse,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        boolean bound = false;

        try {
            final String authHeader = httpServletRequest.getHeader("Authorization");

            // "Bearer ..." é esquema HTTP, não é domínio do token
            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            final String jwt = authHeader.substring(7);

            if (!jwtTokenProvider.validateToken(jwt)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            // ✅ agora é claro: authDomain = TENANT/CONTROLPLANE/...
            final String authDomain = jwtTokenProvider.getAuthDomain(jwt);
            final String username = jwtTokenProvider.getUsernameFromToken(jwt);

         // ✅ TRAVA FORTE (403): token tem que bater com a rota
         // Regra: se tokenType == TENANT e path startsWith /api/admin => 403
         if (requiresControlPlane(httpServletRequest) && "TENANT".equals(authDomain)) {
             httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
             return;
         }

         // (opcional mas recomendado) Regra simétrica: token CONTROLPLANE não entra em /api/tenant => 403
         if (requiresTenant(httpServletRequest) && "CONTROLPLANE".equals(authDomain)) {
             httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
             return;
         }

         // Se chegou aqui, o domínio bate com a área
         if (requiresControlPlane(httpServletRequest) && !"CONTROLPLANE".equals(authDomain)) {
             filterChain.doFilter(httpServletRequest, httpServletResponse);
             return;
         }
         if (requiresTenant(httpServletRequest) && !"TENANT".equals(authDomain)) {
             filterChain.doFilter(httpServletRequest, httpServletResponse);
             return;
         }


            // só aceitamos TENANT / CONTROLPLANE aqui
            if (!"TENANT".equals(authDomain) && !"CONTROLPLANE".equals(authDomain)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            if (!StringUtils.hasText(username)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails;

                if ("TENANT".equals(authDomain)) {
                    final String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(jwt);

                    if (!StringUtils.hasText(tenantSchema) || "public".equalsIgnoreCase(tenantSchema)) {
                        filterChain.doFilter(httpServletRequest, httpServletResponse);
                        return;
                    }

                    if (!tenantSchema.matches("^[a-zA-Z0-9_]+$")) {
                        filterChain.doFilter(httpServletRequest, httpServletResponse);
                        return;
                    }

                    TenantContext.bind(tenantSchema);
                    bound = true;

                    Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
                    if (accountId == null) {
                        filterChain.doFilter(httpServletRequest, httpServletResponse);
                        return;
                    }

                    userDetails = multiContextUserDetailsService.loadTenantUser(username, accountId);

                } else { // CONTROLPLANE
                    String context = jwtTokenProvider.getContextFromToken(jwt);
                    if (StringUtils.hasText(context) && !"public".equalsIgnoreCase(context)) {
                        filterChain.doFilter(httpServletRequest, httpServletResponse);
                        return;
                    }

                    Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
                    if (accountId == null) {
                        filterChain.doFilter(httpServletRequest, httpServletResponse);
                        return;
                    }

                    userDetails = multiContextUserDetailsService.loadControlPlaneUser(username, accountId);
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpServletRequest));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(httpServletRequest, httpServletResponse);

        } finally {
            if (bound) {
                TenantContext.clear();
            }
        }
    }

    private boolean requiresControlPlane(HttpServletRequest httpServletRequest) {
        String path = httpServletRequest.getRequestURI();
        return path.startsWith("/api/admin/") || path.startsWith("/api/controlplane/");
    }

    private boolean requiresTenant(HttpServletRequest httpServletRequest) {
        String path = httpServletRequest.getRequestURI();
        return path.startsWith("/api/tenant/");
    }
}
