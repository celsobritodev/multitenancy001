package brito.com.multitenancy001.shared.security;

import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser simples (fail-fast-friendly) para extrair referências de permissões em expressões SpEL de @PreAuthorize.
 *
 * Cobre os padrões mais comuns do projeto:
 *  1) hasAuthority(T(fqn.PermissionEnum).CONST.asAuthority())
 *  2) hasAuthority(T(fqn.PermissionEnum).CONST.name())
 *  3) hasAuthority(T(fqn.PermissionEnum).CONST)  (raro, mas suportado)
 *  4) hasAuthority('CP_USER_READ') / hasAnyAuthority('TEN_X', 'TEN_Y') (string literal)
 *
 * Não executa SpEL; apenas faz parsing de referências explícitas para validação fail-fast.
 */
public final class PreAuthorizePermissionReferenceParser {

    private PreAuthorizePermissionReferenceParser() {}

    /**
     * Captura: T(fully.qualified.Enum).CONST
     *
     * Exemplos que casam:
     *  - T(brito.com...TenantPermission).TEN_USER_READ.asAuthority()
     *  - T(brito.com...ControlPlanePermission).CP_USER_READ.name()
     */
    private static final Pattern T_ENUM_CONST =
            Pattern.compile("T\\(([^)]+)\\)\\.([A-Z0-9_]+)");

    /**
     * Captura: 'CP_...' ou 'TEN_...' como string literal
     *
     * Exemplos:
     *  - hasAuthority('CP_TENANT_READ')
     *  - hasAnyAuthority('TEN_USER_READ','TEN_PRODUCT_READ')
     */
    private static final Pattern STRING_LITERAL_PERMISSION =
            Pattern.compile("'((?:CP|TEN)_[A-Z0-9_]+)'");

    public static ParsedPermissions parse(String spelExpression) {
        if (!StringUtils.hasText(spelExpression)) {
            return ParsedPermissions.empty();
        }

        String expr = spelExpression.trim();

        LinkedHashSet<EnumConstantRef> enumRefs = new LinkedHashSet<>();
        LinkedHashSet<String> stringCodes = new LinkedHashSet<>();

        // 1) T(Enum).CONST
        Matcher m1 = T_ENUM_CONST.matcher(expr);
        while (m1.find()) {
            String enumFqn = safeTrim(m1.group(1));
            String constant = safeTrim(m1.group(2));
            if (StringUtils.hasText(enumFqn) && StringUtils.hasText(constant)) {
                enumRefs.add(new EnumConstantRef(enumFqn, constant));
            }
        }

        // 2) 'CP_xxx' / 'TEN_xxx'
        Matcher m2 = STRING_LITERAL_PERMISSION.matcher(expr);
        while (m2.find()) {
            String code = safeTrim(m2.group(1));
            if (StringUtils.hasText(code)) {
                stringCodes.add(code);
            }
        }

        return new ParsedPermissions(enumRefs, stringCodes);
    }

    private static String safeTrim(String s) {
        return (s == null) ? null : s.trim();
    }

    // =========================================================
    // DTOs
    // =========================================================

    public record EnumConstantRef(String enumFqn, String constantName) {}

    public record ParsedPermissions(Set<EnumConstantRef> enumConstantRefs,
                                    Set<String> stringLiteralCodes) {
        public static ParsedPermissions empty() {
            return new ParsedPermissions(Set.of(), Set.of());
        }

        public boolean isEmpty() {
            return (enumConstantRefs == null || enumConstantRefs.isEmpty())
                    && (stringLiteralCodes == null || stringLiteralCodes.isEmpty());
        }
    }
}
