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
 * Regras (mantidas):
 * - Sem Bearer: passa
 * - Token inválido: 401 via entryPoint
 * - Mismatch domínio (tenant token em controlplane ou vice-versa): 403 via AccessDeniedHandler
 * - TENANT:
 *   - valida tenantSchema + accountId + X-Tenant
 *   - bind TenantContext.scope(schema)
 *   - (AJUSTE) carrega usuário via TenantUserRepository dentro do schema do tenant (sem MultiContextUserDetailsService)
 * - CONTROLPLANE: mantém MultiContextUserDetailsService
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Mantido para CONTROLPLANE (e outros casos que você ainda queira).
     */
    private final MultiContextUserDetailsService multiContextUserDetailsService;

    /**
     * NOVO: para autenticar TENANT via repository (robusto em schema-per-tenant).
     */
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
        /** comentário: autentica request via Bearer JWT quando aplicável */

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

        // Token inválido/adulterado/expirado => 401 (sem stacktrace)
        if (!jwtTokenProvider.validateToken(jwt)) {
            authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT token"));
            return;
        }

        final AuthDomain authDomain = jwtTokenProvider.getAuthDomainEnum(jwt);
        final String email = normalizeLower(jwtTokenProvider.getEmailFromToken(jwt));

        if (!StringUtils.hasText(email)) {
            authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid JWT claims (email)"));
            return;
        }

        // Se já tem auth no contexto, segue
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
        // Regras de domínio por rota
        // ======================
        boolean needsControlPlane = requiresControlPlane(req);
        boolean needsTenant = requiresTenant(req);

        if (needsControlPlane && authDomain == AuthDomain.TENANT) {
            // ✅ 403 DIRETO
            accessDeniedHandler.handle(req, res,
                    new org.springframework.security.access.AccessDeniedException("Tenant token cannot access control plane routes"));
            return;
        }

        if (needsTenant && authDomain == AuthDomain.CONTROLPLANE) {
            // ✅ 403 DIRETO
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

            // ✅ mismatch do header TEM que ser 403
            final String headerTenant = normalize(req.getHeader(TENANT_HEADER));
            if (StringUtils.hasText(headerTenant) && !headerTenant.equalsIgnoreCase(tenantSchema)) {
                accessDeniedHandler.handle(req, res,
                        new org.springframework.security.access.AccessDeniedException("X-Tenant header does not match token tenant"));
                return;
            }

            // ✅ Bind tenant por TODA a request (daqui pra frente)
            try (TenantContext.Scope ignored = TenantContext.scope(tenantSchema)) {

                // ==========================================================
                // AJUSTE PRINCIPAL:
                // - não usa MultiContextUserDetailsService
                // - busca TenantUser via repository no tenant schema
                // ==========================================================
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

        // Qualquer outro authDomain é inválido => 401 (sem stacktrace)
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(req, res, new BadCredentialsException("Invalid authDomain"));
    }

    /**
     * Mantido para CONTROLPLANE (UserDetails).
     */
    private void setAuth(HttpServletRequest request, UserDetails userDetails) {
        /** comentário: seta Authentication no SecurityContext usando UserDetails */
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * NOVO overload para TENANT (principal tipado + authorities).
     */
    private void setAuth(
            HttpServletRequest request,
            AuthenticatedUserContext principal,
            java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities
    ) {
        /** comentário: seta Authentication no SecurityContext usando principal tipado do tenant */
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private boolean requiresControlPlane(HttpServletRequest req) {
        /** comentário: detecta rotas do controlplane */
        String path = req.getRequestURI();
        return path.startsWith(SecurityConstants.ApiPaths.ADMIN_PREFIX)
                || path.startsWith(SecurityConstants.ApiPaths.CONTROLPLANE_PREFIX);
    }

    private boolean requiresTenant(HttpServletRequest req) {
        /** comentário: detecta rotas tenant (inclui /me) */
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