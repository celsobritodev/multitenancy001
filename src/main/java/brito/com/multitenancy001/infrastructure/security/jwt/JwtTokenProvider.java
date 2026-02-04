package brito.com.multitenancy001.infrastructure.security.jwt;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.time.AppClock;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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

    public String generateControlPlaneToken(Authentication authentication, Long accountId, String context) {
        AuthenticatedUserContext user = (AuthenticatedUserContext) authentication.getPrincipal();

        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_AUTHORITIES, user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(",")))
                .claim(CLAIM_AUTH_DOMAIN, "CONTROLPLANE")
                .claim(CLAIM_CONTEXT, context)
                .claim(CLAIM_ACCOUNT_ID, accountId)
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_ROLE_NAME, user.getRoleName())
                .claim(CLAIM_ROLE_AUTHORITY, user.getRoleAuthority())
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(jwtExpirationInMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generateTenantToken(Authentication authentication, Long accountId, String context) {
        AuthenticatedUserContext user = (AuthenticatedUserContext) authentication.getPrincipal();

        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_AUTHORITIES, user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(",")))
                .claim(CLAIM_AUTH_DOMAIN, "TENANT")
                .claim(CLAIM_CONTEXT, context)
                .claim(CLAIM_ACCOUNT_ID, accountId)
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_ROLE_NAME, user.getRoleName())
                .claim(CLAIM_ROLE_AUTHORITY, user.getRoleAuthority())
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(jwtExpirationInMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Refresh token NÃO precisa de authorities; ele serve para renovar sessão.
     * Guardamos apenas: subject(email) + authDomain + context(tenantSchema) + accountId
     */
    public String generateRefreshToken(String email, String context, Long accountId) {
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_AUTH_DOMAIN, "REFRESH")
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
                .claim(CLAIM_AUTH_DOMAIN, "PASSWORD_RESET")
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

        if ("TENANT".equals(authDomain) && Schemas.CONTROL_PLANE.equalsIgnoreCase(context)) {
            throw new JwtException("Invalid context for TENANT token: public");
        }

        return context;
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
        return "CONTROLPLANE".equals(getAuthDomain(token));
    }

    public boolean isTenantToken(String token) {
        return "TENANT".equals(getAuthDomain(token));
    }
}

