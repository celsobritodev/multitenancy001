package brito.com.multitenancy001.infrastructure.publicschema.auth;

import brito.com.multitenancy001.shared.auth.app.boundary.RefreshTokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Hash canônico de refresh token (SHA-256 hex).
 */
@Component
public class RefreshTokenHasherSha256 implements RefreshTokenHasher {

    @Override
    public String hash(String refreshToken) {
        // Comentário: gera SHA-256 hex estável
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao hashear refreshToken", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
