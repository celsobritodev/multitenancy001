package brito.com.multitenancy001.infrastructure.security.filter;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.SecurityConstants;
import brito.com.multitenancy001.infrastructure.security.authorities.AuthoritiesFactory;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.security.userdetails.MultiContextUserDetailsService;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro JWT.
 *
 * Regras:
 * - Endpoints publicos: bypass explicito (nao tenta validar token)
 * - Sem Bearer: passa
 * - Token invalido: 401 via entryPoint (apenas para rotas protegidas)
 * - Mismatch dominio (tenant token em controlplane ou vice-versa): 403 via AccessDeniedHandler
 * - TENANT:
 *   - valida tenantSchema + accountId + X-Tenant
 *   - bind TenantContext.scope(schema)
 *   - carrega usuario via TenantUserRepository dentro do schema do tenant
 * - CONTROLPLANE: usa MultiContextUserDetailsService
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant";

    private final JwtTokenProvider jwtTokenProvider;
    private final MultiContextUserDetailsService multiContextUserDetailsService;
    private final TenantUserRepository tenantUserRepository;
    private final AppClock appClock;

    private final AuthenticationEntryPoint authenticationEntryPoint; // 401
    private final AccessDeniedHandler accessDeniedHandler;           // 403

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req,
            @NonNull HttpServletResponse res,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        // 0) BYPASS explicito de rotas publicas (Padrão B)
        //    Importante: login/signup nao pode depender de Bearer.
        if (isPublicEndpoint(req)) {
            chain.doFilter(req, res);
            return;
        }

        final String authHeader = req.getHeader("Authorization");

        // 1) Sem Bearer: deixa seguir anonimo; AuthorizationFilter decide depois
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        final String jwt = authHeader.substring(7).trim();

        // 2) Bearer sem token -> trata como "sem token" (nao derruba aqui)
        if (!StringUtils.hasText(jwt)) {
            chain.doFilter(req, res);
            return;
        }

        // 3) Token invalido/adulterado/expirado => 401 (somente em rotas protegidas)
        //    Aqui ja sabemos que nao e rota publica.
        if (!jwtTokenProvider.validateToken(jwt)) {
            // token inválido -> apenas limpa contexto
            // AuthorizationFilter decidirá se rota exige auth
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

        // Se ja tem auth no contexto, segue
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(req, res);
            return;
        }

        // authDomain ausente => token ruim => 401
        if (authDomain == null) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (authDomain)"));
            return;
        }

        // ======================
        // Regras de dominio por rota
        // ======================
        boolean needsControlPlane = requiresControlPlane(req);
        boolean needsTenant = requiresTenant(req);

        if (needsControlPlane && authDomain == AuthDomain.TENANT) {
            accessDeniedHandler.handle(req, res,
                    new org.springframework.security.access.AccessDeniedException("Tenant token cannot access control plane routes"));
            return;
        }

        if (needsTenant && authDomain == AuthDomain.CONTROLPLANE) {
            accessDeniedHandler.handle(req, res,
                    new org.springframework.security.access.AccessDeniedException("Control plane token cannot access tenant routes"));
            return;
        }

        // ======================
        // TENANT
        // ======================
        if (authDomain == AuthDomain.TENANT) {

            final String tenantSchema = normalize(jwtTokenProvider.getTenantSchemaFromToken(jwt));
            if (!StringUtils.hasText(tenantSchema)) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (tenantSchema)"));
                return;
            }
            if (Schemas.CONTROL_PLANE.equalsIgnoreCase(tenantSchema)) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid tenant schema"));
                return;
            }
            if (!tenantSchema.matches("^[a-zA-Z0-9_]+$")) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid tenant schema format"));
                return;
            }

            Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
            if (accountId == null) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (accountId)"));
                return;
            }

            // mismatch do header => 403
            final String headerTenant = normalize(req.getHeader(TENANT_HEADER));
            if (StringUtils.hasText(headerTenant) && !headerTenant.equalsIgnoreCase(tenantSchema)) {
                accessDeniedHandler.handle(req, res,
                        new org.springframework.security.access.AccessDeniedException("X-Tenant header does not match token tenant"));
                return;
            }

            try (TenantContext.Scope ignored = TenantContext.scope(tenantSchema)) {

                TenantUser user = tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(email, accountId)
                        .orElse(null);

                if (user == null) {
                    SecurityContextHolder.clearContext();
                    authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid authentication context"));
                    return;
                }

                if (user.isSuspendedByAccount() || user.isSuspendedByAdmin() || user.isDeleted()) {
                    SecurityContextHolder.clearContext();
                    accessDeniedHandler.handle(req, res,
                            new org.springframework.security.access.AccessDeniedException("User is inactive"));
                    return;
                }

                var authorities = AuthoritiesFactory.forTenant(user);

                AuthenticatedUserContext principal = AuthenticatedUserContext.fromTenantUser(
                        user,
                        tenantSchema,
                        appClock.instant(),
                        authorities
                );

                setAuth(req, principal, authorities);

                chain.doFilter(req, res);
                return;

            } catch (org.springframework.security.access.AccessDeniedException e) {
                SecurityContextHolder.clearContext();
                accessDeniedHandler.handle(req, res, e);
                return;
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid authentication context"));
                return;
            }
        }

        // ======================
        // CONTROLPLANE
        // ======================
        if (authDomain == AuthDomain.CONTROLPLANE) {

            String context = normalize(jwtTokenProvider.getContextFromToken(jwt));
            if (StringUtils.hasText(context) && !Schemas.CONTROL_PLANE.equalsIgnoreCase(context)) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (context)"));
                return;
            }

            Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
            if (accountId == null) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (accountId)"));
                return;
            }

            try {
                UserDetails userDetails = multiContextUserDetailsService.loadControlPlaneUserByEmail(email, accountId);
                setAuth(req, userDetails);
                chain.doFilter(req, res);
                return;
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid authentication context"));
                return;
            }
        }

        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid authDomain"));
    }

    /**
     * Endpoints que devem ser PUBLICOS e nunca depender de Bearer.
     */
    private boolean isPublicEndpoint(HttpServletRequest req) {
        String path = req.getRequestURI();

        if (path == null) {
            return false;
        }

        // signup
        if ("/api/signup".equals(path)) return true;

        // auth endpoints (tenant/controlplane)
        if (path.startsWith("/api/tenant/auth/")) return true;
        if (path.startsWith("/api/controlplane/auth/")) return true;

        // password reset public
        if (path.startsWith("/api/tenant/password/")) return true;

        // health + swagger
        if ("/actuator/health".equals(path)) return true;
        if (path.startsWith("/v3/api-docs/")) return true;
        if (path.startsWith("/swagger-ui")) return true;
        if ("/swagger-ui.html".equals(path)) return true;

        return false;
    }

    private void setAuth(HttpServletRequest request, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setAuth(
            HttpServletRequest request,
            AuthenticatedUserContext principal,
            java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities
    ) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
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

    private String normalizeLower(String s) {
        String t = normalize(s);
        return (t == null) ? null : t.toLowerCase();
    }
}