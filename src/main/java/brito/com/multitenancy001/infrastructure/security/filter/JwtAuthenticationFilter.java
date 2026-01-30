package brito.com.multitenancy001.infrastructure.security.filter;

import brito.com.multitenancy001.infrastructure.security.SecurityConstants;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.security.userdetails.MultiContextUserDetailsService;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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

        final String authDomain = jwtTokenProvider.getAuthDomain(jwt);
        final String emailRaw = jwtTokenProvider.getEmailFromToken(jwt);
        final String email = (emailRaw == null) ? null : emailRaw.trim().toLowerCase();

        if (requiresControlPlane(httpServletRequest) && SecurityConstants.AuthDomains.TENANT.equals(authDomain)) {
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (requiresTenant(httpServletRequest) && SecurityConstants.AuthDomains.CONTROLPLANE.equals(authDomain)) {
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (requiresControlPlane(httpServletRequest) && !SecurityConstants.AuthDomains.CONTROLPLANE.equals(authDomain)) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }
        if (requiresTenant(httpServletRequest) && !SecurityConstants.AuthDomains.TENANT.equals(authDomain)) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        if (!SecurityConstants.AuthDomains.TENANT.equals(authDomain)
                && !SecurityConstants.AuthDomains.CONTROLPLANE.equals(authDomain)) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        if (!StringUtils.hasText(email)) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        // ======================
        // TENANT
        // ======================
        if (SecurityConstants.AuthDomains.TENANT.equals(authDomain)) {
            final String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(jwt);

            if (!StringUtils.hasText(tenantSchema) || Schemas.CONTROL_PLANE.equalsIgnoreCase(tenantSchema)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }
            if (!tenantSchema.matches("^[a-zA-Z0-9_]+$")) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
            if (accountId == null) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            // ✅ se já existe tenant no contexto (vindo do header), não rebindar
            String alreadyBoundTenant = TenantContext.getOrNull();
            if (StringUtils.hasText(alreadyBoundTenant)) {

                UserDetails userDetails = multiContextUserDetailsService.loadTenantUserByEmail(email, accountId);
                setAuth(httpServletRequest, userDetails);
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }

            // ✅ se não existe tenant, usa o do token somente pro load
            try (TenantContext.Scope ignored = TenantContext.scope(tenantSchema)) {
                UserDetails userDetails = multiContextUserDetailsService.loadTenantUserByEmail(email, accountId);
                setAuth(httpServletRequest, userDetails);
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }
        }

        // ======================
        // CONTROLPLANE
        // ======================
        String context = jwtTokenProvider.getContextFromToken(jwt);
        if (StringUtils.hasText(context) && !Schemas.CONTROL_PLANE.equalsIgnoreCase(context)) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
        if (accountId == null) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        UserDetails userDetails = multiContextUserDetailsService.loadControlPlaneUserByEmail(email, accountId);
        setAuth(httpServletRequest, userDetails);
        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    private void setAuth(HttpServletRequest request, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private boolean requiresControlPlane(HttpServletRequest httpServletRequest) {
        String path = httpServletRequest.getRequestURI();
        return path.startsWith(SecurityConstants.ApiPaths.ADMIN_PREFIX)
                || path.startsWith(SecurityConstants.ApiPaths.CONTROLPLANE_PREFIX);
    }

    private boolean requiresTenant(HttpServletRequest httpServletRequest) {
        String path = httpServletRequest.getRequestURI();
        boolean isMe = SecurityConstants.ApiPaths.ME.equals(path)
                || path.startsWith(SecurityConstants.ApiPaths.ME_PREFIX);
        return path.startsWith(SecurityConstants.ApiPaths.TENANT_PREFIX) || isMe;
    }
}
