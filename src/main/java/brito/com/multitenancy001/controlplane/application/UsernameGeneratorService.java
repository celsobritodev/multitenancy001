package brito.com.multitenancy001.controlplane.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class UsernameGeneratorService {
    
    private static final int USERNAME_MAX_LENGTH = 100;
    private static final String USERNAME_PATTERN = "^[a-z0-9._-]+$";

    /**
     * 🔥 Novo método solicitado — versão PRO
     * remove acentos, símbolos e espaços e deixa tudo lowercase
     */
    public String normalize(String input) {
        if (input == null || input.isBlank()) return "user";

        // Remover acentos
        String normalized = java.text.Normalizer
                .normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // Manter apenas letras, números e _
        normalized = normalized
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .toLowerCase()
                .replaceAll("^_|_$", "");

        return normalized.isBlank() ? "user" : normalized;
    }
    
    public String generateFromEmail(String email) {
        String base = email.split("@")[0].toLowerCase();
        base = base.replaceAll("[^a-z0-9._-]", "_");
        base = base.replaceAll("_{2,}", "_");
        base = base.replaceAll("^_|_$", "");

        if (base.isEmpty()) base = "user";

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = base + "_" + suffix;

        if (username.length() > USERNAME_MAX_LENGTH) {
            username = username.substring(0, USERNAME_MAX_LENGTH);
        }

        return username;
    }
    
    public String generateFromNameAndEmail(String name, String email) {
        String namePart = name.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .substring(0, Math.min(name.length(), 20));
        
        String emailPart = email.split("@")[0].toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        
        return namePart + "_" + emailPart + "_" + 
               UUID.randomUUID().toString().substring(0, 6);
    }
    
    public boolean isValidUsername(String username) {
        return username != null && 
               username.length() >= 3 && 
               username.length() <= USERNAME_MAX_LENGTH &&
               username.matches(USERNAME_PATTERN);
    }
    
    public String normalizeUsername(String username) {
        if (username == null) return null;
        return username.toLowerCase()
                .replaceAll("[^a-z0-9._-]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
    }
}
