package brito.com.multitenancy001.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationInMs;

    @Value("${app.jwt.refresh.expiration}")
    private long refreshExpirationInMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 chars)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /* =========================
       TOKEN ACCOUNT (para usuários do sistema account)
       ========================= */
    public String generateAccountToken(
            Authentication authentication,
            Long accountId,
            String tenantSchema
    ) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        return Jwts.builder()
            .subject(user.getUsername())
            .claim("roles", user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(",")))
            .claim("type", "ACCOUNT")
            .claim("tenantSchema", tenantSchema)
            .claim("accountId", accountId)
            .claim("userId", user.getUserId())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    }

    /* =========================
       TOKEN TENANT
       ========================= */
    public String generateTenantToken(
            Authentication authentication,
            Long accountId,
            String tenantSchema
    ) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        return Jwts.builder()
            .subject(user.getUsername())
            .claim("roles", user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(",")))
            .claim("type", "TENANT")
            .claim("tenantSchema", tenantSchema)
            .claim("accountId", accountId)
            .claim("userId", user.getUserId())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    }

    /* =========================
       REFRESH TOKEN (JWT)
       ========================= */
    public String generateRefreshToken(String username, String tenantSchema) {
        return Jwts.builder()
            .subject(username)
            .claim("type", "REFRESH")
            .claim("tenantSchema", tenantSchema)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshExpirationInMs))
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    }

    /* =========================
       PASSWORD RESET TOKEN
       ========================= */
    public String generatePasswordResetToken(
            String username,
            String tenantSchema,
            Long accountId
    ) {
        return Jwts.builder()
            .subject(username)
            .claim("type", "PASSWORD_RESET")
            .claim("tenantSchema", tenantSchema)
            .claim("accountId", accountId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1h
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

    public String getTenantSchemaFromToken(String token) {
        return getAllClaimsFromToken(token).get("tenantSchema", String.class);
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

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            return expiration.before(new Date());
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
    public boolean isAccountToken(String token) {
        return "ACCOUNT".equals(getTokenType(token));
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
}