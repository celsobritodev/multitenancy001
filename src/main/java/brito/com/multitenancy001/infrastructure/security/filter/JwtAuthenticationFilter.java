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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final MultiContextUserDetailsService multiContextUserDetailsService;
    private final AuthenticationEntryPoint authenticationEntryPoint; // ✅ novo

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req,
            @NonNull HttpServletResponse res,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        final String authHeader = req.getHeader("Authorization");

        // Sem Bearer: deixa o Spring decidir (permitAll passa; protegido vira 401 via entryPoint global)
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        final String jwt = authHeader.substring(7).trim();
        if (!StringUtils.hasText(jwt)) {
            authenticationEntryPoint.commence(req, res, new BadCredentialsException("Missing JWT token"));
            return;
        }

        // ✅ Token inválido/adulterado/expirado => 401 (sem stacktrace)
        if (!jwtTokenProvider.validateToken(jwt)) {
            authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT token"));
            return;
        }

        final String authDomain = jwtTokenProvider.getAuthDomain(jwt);
        final String emailRaw = jwtTokenProvider.getEmailFromToken(jwt);
        final String email = (emailRaw == null) ? null : emailRaw.trim().toLowerCase();

        if (!StringUtils.hasText(email)) {
            authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (email)"));
            return;
        }

        // Já autenticado? segue
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(req, res);
            return;
        }

        // ======================
        // Regras de domínio por rota
        // ======================
        boolean needsControlPlane = requiresControlPlane(req);
        boolean needsTenant = requiresTenant(req);

        if (needsControlPlane && SecurityConstants.AuthDomains.TENANT.equals(authDomain)) {
            // token TENANT tentando acessar rota controlplane/admin => 403
            throw new AccessDeniedException("Tenant token cannot access control plane routes");
        }
        if (needsTenant && SecurityConstants.AuthDomains.CONTROLPLANE.equals(authDomain)) {
            // token CONTROLPLANE tentando acessar rota tenant => 403
            throw new AccessDeniedException("Control plane token cannot access tenant routes");
        }

        // ======================
        // TENANT
        // ======================
        if (SecurityConstants.AuthDomains.TENANT.equals(authDomain)) {

            final String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(jwt);
            if (!StringUtils.hasText(tenantSchema)) {
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (tenantSchema)"));
                return;
            }
            if (Schemas.CONTROL_PLANE.equalsIgnoreCase(tenantSchema)) {
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid tenant schema"));
                return;
            }
            if (!tenantSchema.matches("^[a-zA-Z0-9_]+$")) {
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid tenant schema format"));
                return;
            }

            Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
            if (accountId == null) {
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (accountId)"));
                return;
            }

            // Se mandarem X-Tenant, valida coerência (evita confusão e spoof)
            // ⚠️ Se você removeu o TenantHeaderTenantContextFilter do projeto, use "X-Tenant" direto aqui.
            final String headerTenant = normalize(req.getHeader(TenantHeaderTenantContextFilter.TENANT_HEADER));
            if (StringUtils.hasText(headerTenant) && !headerTenant.equalsIgnoreCase(tenantSchema)) {
                throw new AccessDeniedException("X-Tenant header does not match token tenant");
            }

            // ✅ Bind tenant por TODA a request (daqui pra frente)
            try (TenantContext.Scope ignored = TenantContext.scope(tenantSchema)) {
                UserDetails userDetails = multiContextUserDetailsService.loadTenantUserByEmail(email, accountId);
                setAuth(req, userDetails);
                chain.doFilter(req, res);
                return;
            }
        }

        // ======================
        // CONTROLPLANE
        // ======================
        if (SecurityConstants.AuthDomains.CONTROLPLANE.equals(authDomain)) {
            String context = jwtTokenProvider.getContextFromToken(jwt);
            if (StringUtils.hasText(context) && !Schemas.CONTROL_PLANE.equalsIgnoreCase(context)) {
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (context)"));
                return;
            }

            Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
            if (accountId == null) {
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (accountId)"));
                return;
            }

            UserDetails userDetails = multiContextUserDetailsService.loadControlPlaneUserByEmail(email, accountId);
            setAuth(req, userDetails);
            chain.doFilter(req, res);
            return;
        }

        // Qualquer outro authDomain é inválido => 401 (sem stacktrace)
        authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid authDomain"));
    }

    private void setAuth(HttpServletRequest request, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private boolean requiresControlPlane(HttpServletRequest req) {
        String path = req.getRequestURI();
        return path.startsWith(SecurityConstants.ApiPaths.ADMIN_PREFIX)
                || path.startsWith(SecurityConstants.ApiPaths.CONTROLPLANE_PREFIX);
    }

    private boolean requiresTenant(HttpServletRequest req) {
        String path = req.getRequestURI();
        boolean isMe = SecurityConstants.ApiPaths.ME.equals(path)
                || path.startsWith(SecurityConstants.ApiPaths.ME_PREFIX);
        return path.startsWith(SecurityConstants.ApiPaths.TENANT_PREFIX) || isMe;
    }

    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
