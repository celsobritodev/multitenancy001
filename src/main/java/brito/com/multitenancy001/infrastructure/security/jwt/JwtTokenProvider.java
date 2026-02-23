package brito.com.multitenancy001.infrastructure.security.jwt;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.SecurityConstants;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provider central de JWT da aplicação.
 *
 * Regras:
 * - Usa AppClock como fonte única de tempo.
 * - NÃO assume que authentication.getPrincipal() é sempre AuthenticatedUserContext:
 *   em alguns fluxos pode vir UserDetails (ex.: org.springframework.security.core.userdetails.User).
 * - Para manter claims consistentes (userId/roleName/roleAuthority), o caller pode (e deve)
 *   passar o userId (subject_id) quando o principal não for AuthenticatedUserContext.
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_AUTH_DOMAIN = "authDomain";
    public static final String CLAIM_CONTEXT = "context";
    public static final String CLAIM_ACCOUNT_ID = "accountId";
    public static final String CLAIM_USER_ID = "userId";

    public static final String CLAIM_ROLE_NAME = "roleName";
    public static final String CLAIM_ROLE_AUTHORITY = "roleAuthority";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationInMs;

    @Value("${app.jwt.refresh.expiration}")
    private long refreshExpirationInMs;

    private final AppClock appClock;
    private SecretKey key;

    public JwtTokenProvider(AppClock appClock) {
        this.appClock = appClock;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 chars)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    private Date issuedAt() {
        return Date.from(appClock.instant());
    }

    private Date expiresAtInMs(long ttlMillis) {
        Instant exp = appClock.instant().plusMillis(ttlMillis);
        return Date.from(exp);
    }

    /**
     * Gera Access Token do Control Plane.
     *
     * IMPORTANTE:
     * - Se o principal NÃO for AuthenticatedUserContext, você DEVE passar userId (subject_id),
     *   senão o token não consegue preencher CLAIM_USER_ID com segurança.
     */
    public String generateControlPlaneToken(Authentication authentication, Long accountId, String context, Long userId) {
        /* Resolve principal de forma segura */
        ResolvedPrincipal p = resolvePrincipal(authentication, userId);

        return Jwts.builder()
                .subject(p.email())
                .claim(CLAIM_AUTHORITIES, p.authoritiesCsv())
                .claim(CLAIM_AUTH_DOMAIN, SecurityConstants.AuthDomains.CONTROLPLANE)
                .claim(CLAIM_CONTEXT, context)
                .claim(CLAIM_ACCOUNT_ID, accountId)
                .claim(CLAIM_USER_ID, p.userId())
                .claim(CLAIM_ROLE_NAME, p.roleName())
                .claim(CLAIM_ROLE_AUTHORITY, p.roleAuthority())
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(jwtExpirationInMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Backward-compatible (mantém assinatura antiga).
     *
     * Regras:
     * - Mantido para não quebrar callers antigos.
     * - Se o principal não for AuthenticatedUserContext, vai falhar com erro claro,
     *   porque esta assinatura não permite informar userId.
     */
    public String generateControlPlaneToken(Authentication authentication, Long accountId, String context) {
        return generateControlPlaneToken(authentication, accountId, context, null);
    }

    /**
     * Gera Access Token de Tenant.
     *
     * IMPORTANTE:
     * - Se o principal NÃO for AuthenticatedUserContext, você DEVE passar userId (subject_id),
     *   senão o token não consegue preencher CLAIM_USER_ID com segurança.
     */
    public String generateTenantToken(Authentication authentication, Long accountId, String context, Long userId) {
        /* Resolve principal de forma segura */
        ResolvedPrincipal p = resolvePrincipal(authentication, userId);

        return Jwts.builder()
                .subject(p.email())
                .claim(CLAIM_AUTHORITIES, p.authoritiesCsv())
                .claim(CLAIM_AUTH_DOMAIN, SecurityConstants.AuthDomains.TENANT)
                .claim(CLAIM_CONTEXT, context)
                .claim(CLAIM_ACCOUNT_ID, accountId)
                .claim(CLAIM_USER_ID, p.userId())
                .claim(CLAIM_ROLE_NAME, p.roleName())
                .claim(CLAIM_ROLE_AUTHORITY, p.roleAuthority())
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(jwtExpirationInMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Backward-compatible (mantém assinatura antiga).
     */
    public String generateTenantToken(Authentication authentication, Long accountId, String context) {
        return generateTenantToken(authentication, accountId, context, null);
    }

    /**
     * Refresh token NÃO precisa de authorities; ele serve para renovar sessão.
     * Guardamos apenas: subject(email) + authDomain + context(tenantSchema) + accountId
     *
     * IMPORTANTE:
     * - Sempre incluir jti/id aleatório para garantir rotação real (token string muda)
     *   mesmo quando emitido no mesmo millisecond.
     */
    public String generateRefreshToken(String email, String context, Long accountId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString()) // ✅ garante token diferente sempre
                .subject(email)
                .claim(CLAIM_AUTH_DOMAIN, SecurityConstants.AuthDomains.REFRESH)
                .claim(CLAIM_CONTEXT, context)
                .claim(CLAIM_ACCOUNT_ID, accountId)
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(refreshExpirationInMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generatePasswordResetToken(String email, String context, Long accountId) {
        long oneHourMs = 3_600_000L;

        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_AUTH_DOMAIN, SecurityConstants.AuthDomains.PASSWORD_RESET)
                .claim(CLAIM_CONTEXT, context)
                .claim(CLAIM_ACCOUNT_ID, accountId)
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(oneHourMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmailFromToken(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }

    public String getContextFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);

        String context = claims.get(CLAIM_CONTEXT, String.class);
        if (context == null) context = claims.get("tenantSchema", String.class);

        String authDomain = getAuthDomain(token);

        if (SecurityConstants.AuthDomains.is(authDomain, AuthDomain.TENANT)
                && Schemas.CONTROL_PLANE.equalsIgnoreCase(context)) {

            throw new JwtException("Invalid context for TENANT token: public");
        }

        return context;
    }

    public AuthDomain getAuthDomainEnum(String token) {
        return SecurityConstants.AuthDomains.parseOrNull(getAuthDomain(token));
    }

    public String getTenantSchemaFromToken(String token) {
        return getContextFromToken(token);
    }

    public Long getAccountIdFromToken(String token) {
        return getAllClaimsFromToken(token).get(CLAIM_ACCOUNT_ID, Long.class);
    }

    public Long getUserIdFromToken(String token) {
        return getAllClaimsFromToken(token).get(CLAIM_USER_ID, Long.class);
    }

    public String getAuthDomain(String token) {
        Claims claims = getAllClaimsFromToken(token);

        String authDomain = claims.get(CLAIM_AUTH_DOMAIN, String.class);
        if (!StringUtils.hasText(authDomain)) {
            authDomain = claims.get("type", String.class); // compat tokens antigos
        }

        return authDomain;
    }

    public String getRoleNameFromToken(String token) {
        return getAllClaimsFromToken(token).get(CLAIM_ROLE_NAME, String.class);
    }

    public String getRoleAuthorityFromToken(String token) {
        return getAllClaimsFromToken(token).get(CLAIM_ROLE_AUTHORITY, String.class);
    }

    public List<String> getAuthoritiesFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);

        String authorities = claims.get(CLAIM_AUTHORITIES, String.class);
        if (!StringUtils.hasText(authorities)) {
            authorities = claims.get("roles", String.class); // compat tokens antigos
        }

        return splitCsv(authorities);
    }

    private List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();

        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            return expiration.before(Date.from(appClock.instant()));
        } catch (Exception e) {
            return true;
        }
    }

    public boolean validateToken(String token) {
        try {
            getAllClaimsFromToken(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isControlPlaneToken(String token) {
        return SecurityConstants.AuthDomains.is(getAuthDomain(token), AuthDomain.CONTROLPLANE);
    }

    public boolean isTenantToken(String token) {
        return SecurityConstants.AuthDomains.is(getAuthDomain(token), AuthDomain.TENANT);
    }

    /**
     * Resolve o "principal" de forma segura para geração do token.
     *
     * Regras:
     * - Se for AuthenticatedUserContext: usa tudo dele (userId, roleName, roleAuthority, authorities).
     * - Se for UserDetails/qualquer outro: usa email + authorities do Authentication, e exige userId explícito.
     */
    private ResolvedPrincipal resolvePrincipal(Authentication authentication, Long explicitUserId) {

        Object principal = authentication == null ? null : authentication.getPrincipal();

        // Caso 1: seu principal custom (melhor cenário)
        if (principal instanceof AuthenticatedUserContext user) {
            String authoritiesCsv = user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));

            return new ResolvedPrincipal(
                    user.getEmail(),
                    authoritiesCsv,
                    user.getUserId(),
                    user.getRoleName(),
                    user.getRoleAuthority()
            );
        }

        // Caso 2: principal do Spring (org.springframework.security.core.userdetails.User etc.)
        // Aqui NÃO existe userId/roleName/roleAuthority, então o caller deve passar userId (subject_id).
        if (explicitUserId == null) {
            throw new ApiException(
                    ApiErrorCode.INTERNAL_SERVER_ERROR,
                    "Principal não é AuthenticatedUserContext; informe userId (subject_id) ao gerar o token",
                    new PrincipalDetails(principal, authentication)
            );
        }

        String email = resolveEmail(authentication, principal);

        String authoritiesCsv = (authentication == null ? List.<GrantedAuthority>of() : authentication.getAuthorities())
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        // Tentativa best-effort de roleAuthority para manter claim preenchida:
        // pega a primeira authority que pareça ser "role".
        String roleAuthority = guessRoleAuthority(authentication);
        String roleName = null; // não dá para inferir com 100% de certeza sem seu principal custom

        return new ResolvedPrincipal(
                email,
                authoritiesCsv,
                explicitUserId,
                roleName,
                roleAuthority
        );
    }

    /**
     * Resolve o email (subject) de forma robusta.
     */
    private String resolveEmail(Authentication authentication, Object principal) {
        // 1) principal UserDetails
        if (principal instanceof UserDetails ud && StringUtils.hasText(ud.getUsername())) {
            return ud.getUsername();
        }

        // 2) authentication.getName()
        if (authentication != null && StringUtils.hasText(authentication.getName())) {
            return authentication.getName();
        }

        // 3) fallback hard
        throw new ApiException(
                ApiErrorCode.INTERNAL_SERVER_ERROR,
                "Não foi possível resolver email do principal para geração de token",
                new PrincipalDetails(principal, authentication)
        );
    }

    /**
     * Heurística para tentar manter "roleAuthority" preenchida quando o principal não é o seu contexto custom.
     */
    private String guessRoleAuthority(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) return null;

        List<String> auths = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(StringUtils::hasText)
                .toList();

        // Preferência 1: ROLE_*
        for (String a : auths) {
            if (a.startsWith("ROLE_")) return a;
        }

        // Preferência 2: algo com CONTROLPLANE_ ou TENANT_ (depende do seu padrão)
        for (String a : auths) {
            if (a.contains("CONTROLPLANE_") || a.contains("TENANT_")) return a;
        }

        // Fallback: primeira authority, se existir
        return auths.isEmpty() ? null : auths.get(0);
    }

    /**
     * DTO interno para evitar casts e manter claims consistentes.
     */
    private record ResolvedPrincipal(
            String email,
            String authoritiesCsv,
            Long userId,
            String roleName,
            String roleAuthority
    ) {}

    /**
     * Details estruturado para facilitar debug/observability.
     */
    private record PrincipalDetails(
            String principalType,
            String authName,
            List<String> authorities
    ) {
        private PrincipalDetails(Object principal, Authentication authentication) {
            this(
                    principal == null ? "null" : principal.getClass().getName(),
                    authentication == null ? null : authentication.getName(),
                    authentication == null ? List.of()
                            : authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .toList()
            );
        }
    }
}