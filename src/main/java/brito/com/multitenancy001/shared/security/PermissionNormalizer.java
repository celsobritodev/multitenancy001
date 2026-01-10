package brito.com.multitenancy001.shared.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class PermissionNormalizer {

    private PermissionNormalizer() {}

    public static Set<String> normalizeTenant(Collection<String> perms) {
        Set<String> out = new HashSet<>();
        if (perms == null) return out;
        for (String p : perms) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            // bloqueia perm de control plane no tenant
            if (x.startsWith("CP_")) {
                throw new IllegalArgumentException("Permission de Control Plane não é permitida no Tenant: " + x);
            }

            // se não tiver prefixo TEN_, adiciona
            if (!x.startsWith("TEN_")) x = "TEN_" + x;
            out.add(x);
        }
        return out;
    }

    public static Set<String> normalizeControlPlane(Collection<String> perms) {
        Set<String> out = new HashSet<>();
        if (perms == null) return out;
        for (String p : perms) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            // bloqueia perm de tenant no control plane
            if (x.startsWith("TEN_")) {
                throw new IllegalArgumentException("Permission de Tenant não é permitida no Control Plane: " + x);
            }

            if (!x.startsWith("CP_")) x = "CP_" + x;
            out.add(x);
        }
        return out;
    }
}
