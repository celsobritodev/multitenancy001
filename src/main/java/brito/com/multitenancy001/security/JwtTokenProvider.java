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

    
    
    public String getTokenType(String token) {
        return getAllClaimsFromToken(token).get("type", String.class);
    }

    

    /* =========================
       TOKEN PLATFORM (SUPER ADMIN)
       ========================= */
    public String generatePlatformToken(Authentication authentication) {

        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        return Jwts.builder()
            .subject(user.getUsername())
            .claim("role", "SUPER_ADMIN")
            .claim("type", "PLATFORM")
            .claim("tenantSchema", "public")
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

    public boolean validateToken(String token) {
        try {
            getAllClaimsFromToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
