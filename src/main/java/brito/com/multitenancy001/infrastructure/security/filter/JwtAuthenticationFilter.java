package brito.com.multitenancy001.infrastructure.security.filter;

import java.io.IOException;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.SecurityConstants;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.security.userdetails.MultiContextUserDetailsService;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro JWT endurecido.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Bypass explícito de endpoints públicos.</li>
 *   <li>Validar token e claims essenciais.</li>
 *   <li>Validar compatibilidade entre rota, domínio e tenant header/context.</li>
 *   <li>Delegar carregamento do principal para MultiContextUserDetailsService.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Não abre TenantContext.</li>
 *   <li>Consome o TenantContext já bindado pelo TenantHeaderTenantContextFilter.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final MultiContextUserDetailsService multiContextUserDetailsService;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req,
            @NonNull HttpServletResponse res,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        if (isPublicEndpoint(req)) {
            chain.doFilter(req, res);
            return;
        }

        final String authHeader = req.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        final String jwt = authHeader.substring(7).trim();
        if (!StringUtils.hasText(jwt)) {
            chain.doFilter(req, res);
            return;
        }

        if (!jwtTokenProvider.validateToken(jwt)) {
            SecurityContextHolder.clearContext();
            chain.doFilter(req, res);
            return;
        }

        final AuthDomain authDomain = jwtTokenProvider.getAuthDomainEnum(jwt);
        final String email = normalizeLower(jwtTokenProvider.getEmailFromToken(jwt));

        if (!StringUtils.hasText(email)) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (email)"));
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(req, res);
            return;
        }

        if (authDomain == null) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (authDomain)"));
            return;
        }

        boolean needsControlPlane = requiresControlPlane(req);
        boolean needsTenant = requiresTenant(req);

        if (needsControlPlane && authDomain == AuthDomain.TENANT) {
            deny(req, res, "Tenant token cannot access control plane routes");
            return;
        }

        if (needsTenant && authDomain == AuthDomain.CONTROLPLANE) {
            deny(req, res, "Control plane token cannot access tenant routes");
            return;
        }

        if (authDomain == AuthDomain.TENANT) {
            authenticateTenant(req, res, chain, jwt, email);
            return;
        }

        if (authDomain == AuthDomain.CONTROLPLANE) {
            authenticateControlPlane(req, res, chain, jwt, email);
            return;
        }

        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid authDomain"));
    }

    private void authenticateTenant(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain,
            String jwt,
            String email
    ) throws IOException, ServletException {

        final String tenantSchema = normalize(jwtTokenProvider.getTenantSchemaFromToken(jwt));
        if (!StringUtils.hasText(tenantSchema)) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res,
                    new BadCredentialsException("Invalid JWT claims (tenantSchema)"));
            return;
        }

        if (Schemas.CONTROL_PLANE.equalsIgnoreCase(tenantSchema)) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res,
                    new BadCredentialsException("Invalid tenant schema"));
            return;
        }

        if (!tenantSchema.matches("^[a-zA-Z0-9_]+$")) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res,
                    new BadCredentialsException("Invalid tenant schema format"));
            return;
        }

        Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
        if (accountId == null) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res,
                    new BadCredentialsException("Invalid JWT claims (accountId)"));
            return;
        }

        String boundTenant = TenantContext.getOrNull();
        if (!StringUtils.hasText(boundTenant)) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res,
                    new BadCredentialsException("Tenant context is required for tenant routes"));
            return;
        }

        if (!tenantSchema.equalsIgnoreCase(boundTenant)) {
            deny(req, res, "X-Tenant header / TenantContext does not match token tenant");
            return;
        }

        try {
            AuthenticatedUserContext principal =
                    multiContextUserDetailsService.loadTenantAuthenticatedUserByEmail(email, accountId, tenantSchema);

            setAuth(req, principal);
            chain.doFilter(req, res);
        } catch (AccessDeniedException e) {
            SecurityContextHolder.clearContext();
            accessDeniedHandler.handle(req, res, e);
        } catch (Exception e) {
            log.warn("Falha ao autenticar tenant via JWT. email={} tenant={} motivo={}",
                    email, tenantSchema, safeMsg(e));
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res,
                    new BadCredentialsException("Invalid authentication context"));
        }
    }

    private void authenticateControlPlane(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain,
            String jwt,
            String email
    ) throws IOException, ServletException {

        String context = normalize(jwtTokenProvider.getContextFromToken(jwt));
        if (StringUtils.hasText(context) && !Schemas.CONTROL_PLANE.equalsIgnoreCase(context)) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res,
                    new BadCredentialsException("Invalid JWT claims (context)"));
            return;
        }

        Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
        if (accountId == null) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res,
                    new BadCredentialsException("Invalid JWT claims (accountId)"));
            return;
        }

        try {
            UserDetails userDetails = multiContextUserDetailsService.loadControlPlaneUserByEmail(email, accountId);
            setAuth(req, userDetails);
            chain.doFilter(req, res);
        } catch (Exception e) {
            log.warn("Falha ao autenticar control plane via JWT. email={} motivo={}", email, safeMsg(e));
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res,
                    new BadCredentialsException("Invalid authentication context"));
        }
    }

    private void setAuth(HttpServletRequest request, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void deny(HttpServletRequest req, HttpServletResponse res, String message) throws IOException, ServletException {
        accessDeniedHandler.handle(req, res, new AccessDeniedException(message));
    }

    private boolean isPublicEndpoint(HttpServletRequest req) {
        String path = req.getRequestURI();
        if (path == null) {
            return false;
        }

        if ("/api/signup".equals(path)) return true;
        if (path.startsWith("/api/tenant/auth/")) return true;
        if (path.startsWith("/api/controlplane/auth/")) return true;
        if (path.startsWith("/api/tenant/password/")) return true;
        if ("/actuator/health".equals(path)) return true;
        if (path.startsWith("/v3/api-docs/")) return true;
        if (path.startsWith("/swagger-ui")) return true;
        if ("/swagger-ui.html".equals(path)) return true;

        return false;
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

    private String normalizeLower(String s) {
        String t = normalize(s);
        return (t == null) ? null : t.toLowerCase();
    }

    private String safeMsg(Throwable e) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return e == null ? "erro desconhecido" : e.getClass().getSimpleName();
        }
        return e.getMessage();
    }
}