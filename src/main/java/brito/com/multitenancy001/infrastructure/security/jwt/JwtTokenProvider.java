package brito.com.multitenancy001.infrastructure.security.jwt;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
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

    /* =========================
       TOKEN CONTROLPLANE
       ========================= */
    public String generateControlPlaneToken(
            Authentication authentication,
            Long accountId,
            String context
    ) {
        AuthenticatedUserContext user = (AuthenticatedUserContext) authentication.getPrincipal();

        return Jwts.builder()
                .subject(user.getUsername())
                // ✅ permission-first: authorities = CP_*
                .claim("authorities", user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(",")))
                .claim("type", "CONTROLPLANE")
                .claim("context", context)
                .claim("accountId", accountId)
                .claim("userId", user.getUserId())
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(jwtExpirationInMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /* =========================
       TOKEN TENANT
       ========================= */
    public String generateTenantToken(
            Authentication authentication,
            Long accountId,
            String context
    ) {
        AuthenticatedUserContext user = (AuthenticatedUserContext) authentication.getPrincipal();

        return Jwts.builder()
                .subject(user.getUsername())
                // ✅ permission-first: authorities = TEN_*
                .claim("authorities", user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(",")))
                .claim("type", "TENANT")
                .claim("context", context)
                .claim("accountId", accountId)
                .claim("userId", user.getUserId())
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(jwtExpirationInMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /* =========================
       REFRESH TOKEN
       ========================= */
    public String generateRefreshToken(String username, String context) {
        return Jwts.builder()
                .subject(username)
                .claim("type", "REFRESH")
                .claim("context", context)
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(refreshExpirationInMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /* =========================
       PASSWORD RESET TOKEN
       ========================= */
    public String generatePasswordResetToken(String username, String context, Long accountId) {
        long oneHourMs = 3_600_000L;

        return Jwts.builder()
                .subject(username)
                .claim("type", "PASSWORD_RESET")
                .claim("context", context)
                .claim("accountId", accountId)
                .issuedAt(issuedAt())
                .expiration(expiresAtInMs(oneHourMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /* =========================
       LEITURA DE CLAIMS
       ========================= */
    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromToken(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }

    /**
     * Obtém o contexto do token (antigo tenantSchema)
     * Mantém compatibilidade retornando "tenantSchema" se "context" não existir
     */
    public String getContextFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);

        String context = claims.get("context", String.class);
        if (context == null) {
            context = claims.get("tenantSchema", String.class);
        }

        String type = claims.get("type", String.class);

        // ✅ Só TENANT não pode ter public
        if ("TENANT".equals(type) && "public".equalsIgnoreCase(context)) {
            throw new JwtException("Invalid context for TENANT token: public");
        }

        return context;
    }

    public String getTenantSchemaFromToken(String token) {
        return getContextFromToken(token);
    }

    public Long getAccountIdFromToken(String token) {
        return getAllClaimsFromToken(token).get("accountId", Long.class);
    }

    public String getTokenType(String token) {
        return getAllClaimsFromToken(token).get("type", String.class);
    }

    public Long getUserIdFromToken(String token) {
        return getAllClaimsFromToken(token).get("userId", Long.class);
    }

    /**
     * ✅ NOVO: lê authorities do token (permission-first).
     * Compatível com tokens antigos que ainda tenham "roles".
     */
    public List<String> getAuthoritiesFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);

        String authorities = claims.get("authorities", String.class);

        if (!StringUtils.hasText(authorities)) {
            authorities = claims.get("roles", String.class);
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

    /* =========================
       MÉTODOS AUXILIARES
       ========================= */
    public boolean isControlPlaneToken(String token) {
        return "CONTROLPLANE".equals(getTokenType(token));
    }

    public boolean isTenantToken(String token) {
        return "TENANT".equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return "REFRESH".equals(getTokenType(token));
    }

    public boolean isPasswordResetToken(String token) {
        return "PASSWORD_RESET".equals(getTokenType(token));
    }

    public boolean isTokenInContext(String token, String expectedContext) {
        String actualContext = getContextFromToken(token);
        return expectedContext.equals(actualContext);
    }

    public boolean isControlPlaneContextToken(String token) {
        String context = getContextFromToken(token);
        return "public".equals(context);
    }
}
