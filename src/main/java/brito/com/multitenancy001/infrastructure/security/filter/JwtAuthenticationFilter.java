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

            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            final String jwt = authHeader.substring(7);

            if (!jwtTokenProvider.validateToken(jwt)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            final String tokenType = jwtTokenProvider.getTokenType(jwt);
            final String username = jwtTokenProvider.getUsernameFromToken(jwt);

            // ✅ TRAVA: token tem que bater com a rota
            if (requiresControlPlane(httpServletRequest) && !"CONTROLPLANE".equals(tokenType)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }
            if (requiresTenant(httpServletRequest) && !"TENANT".equals(tokenType)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            // só aceitamos TENANT / CONTROLPLANE aqui
            if (!"TENANT".equals(tokenType) && !"CONTROLPLANE".equals(tokenType)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            if (!StringUtils.hasText(username)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails;

                if ("TENANT".equals(tokenType)) {
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
