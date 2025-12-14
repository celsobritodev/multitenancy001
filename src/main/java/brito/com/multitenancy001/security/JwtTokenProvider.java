package brito.com.multitenancy001.security;



import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;





@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationInMs;

    @Value("${app.jwt.refresh.expiration}")
    private long refreshExpirationInMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 characters)");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication, Long accountId, String tenantSchema) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(",")));
        claims.put("accountId", accountId);
        claims.put("tenantSchema", tenantSchema);

        if (userDetails instanceof CustomUserDetails) {
            CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
            claims.put("userId", customUserDetails.getId());
            claims.put("email", customUserDetails.getEmail());
        }

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(String username, String tenantSchema) {
        return Jwts.builder()
                .subject(username)
                .claim("tenantSchema", tenantSchema)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationInMs))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }


    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public Long getAccountIdFromToken(String token) {
        return getAllClaimsFromToken(token).get("accountId", Long.class);
    }

    public String getTenantSchemaFromToken(String token) {
        return getAllClaimsFromToken(token).get("tenantSchema", String.class);
    }

    public Long getUserIdFromToken(String token) {
        return getAllClaimsFromToken(token).get("userId", Long.class);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public Boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = getAllClaimsFromToken(token);

        String username = claims.getSubject();
        String roles = claims.get("roles", String.class);

        Collection<? extends GrantedAuthority> authorities = Arrays.stream(roles.split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                username, "", authorities);

        return new UsernamePasswordAuthenticationToken(userDetails, token, authorities);
    }

    public String generatePasswordResetToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim("type", "password_reset")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public boolean isPasswordResetToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return "password_reset".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public String generateEmailVerificationToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("type", "email_verification")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000)) // 24h
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }



@PostConstruct
public void debug() {
    System.out.println(">>>> LOADED SECRET: [" + jwtSecret + "]");
}

}

