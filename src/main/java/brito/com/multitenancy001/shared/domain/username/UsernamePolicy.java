package brito.com.multitenancy001.shared.domain.username;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class UsernamePolicy {

    public static final String USERNAME_REGEX = "^[a-z0-9._-]+$";
    public static final int USERNAME_MAX_LENGTH = 100;

    private static final Pattern USERNAME_PATTERN = Pattern.compile(USERNAME_REGEX);

    private static final String FALLBACK_BASE = "user";
    private static final String SEPARATOR = "_";

    public String normalizeBase(String raw) {
        if (raw == null || raw.isBlank()) return FALLBACK_BASE;

        String base = raw.toLowerCase();
        base = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        base = base.replaceAll("[^a-z0-9._-]", "_")
                   .replaceAll("_{2,}", "_")
                   .replaceAll("^_|_$", "");

        return base.isBlank() ? FALLBACK_BASE : base;
    }

    public boolean isValid(String username) {
        if (username == null) return false;
        if (username.length() < 3 || username.length() > USERNAME_MAX_LENGTH) return false;
        return USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * Retorna um candidato "puro" (sem sufixo), já normalizado e truncado no limite do banco.
     * Ex.: "Vendas@..." -> "vendas"
     */
    public String asCandidate(String rawBase) {
        String base = normalizeBase(rawBase);

        if (base.length() > USERNAME_MAX_LENGTH) {
            base = base.substring(0, USERNAME_MAX_LENGTH).replaceAll("_+$", "");
            if (base.isBlank()) base = "u";
        }

        if (base.length() < 3) {
            base = FALLBACK_BASE;
        }

        return base;
    }

    /**
     * Monta base + "_" + suffix respeitando max length, cortando só base.
     * Ex.: vendas + 2 -> vendas_2
     */
    public String build(String base, String suffix) {
        Objects.requireNonNull(suffix, "suffix");

        int maxBaseLen = USERNAME_MAX_LENGTH - SEPARATOR.length() - suffix.length();
        if (maxBaseLen <= 0) {
            base = "u";
            maxBaseLen = 1;
        }

        base = normalizeBase(base);

        if (base.length() > maxBaseLen) {
            base = base.substring(0, maxBaseLen).replaceAll("_+$", "");
            if (base.isBlank()) base = "u";
        }

        return base + SEPARATOR + suffix;
    }

    public String extractBase(String username) {
        if (username == null) return FALLBACK_BASE;
        int idx = username.lastIndexOf(SEPARATOR);
        if (idx <= 0) return normalizeBase(username);
        return normalizeBase(username.substring(0, idx));
    }
}
