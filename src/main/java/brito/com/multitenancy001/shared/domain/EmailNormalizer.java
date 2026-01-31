package brito.com.multitenancy001.shared.domain;

public final class EmailNormalizer {

    private EmailNormalizer() { }

    public static String normalizeOrNull(String email) {
        if (email == null) return null;
        String e = email.trim();
        if (e.isEmpty()) return null;
        return e.toLowerCase();
    }
}
