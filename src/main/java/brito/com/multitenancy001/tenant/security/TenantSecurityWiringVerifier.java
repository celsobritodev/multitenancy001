package brito.com.multitenancy001.tenant.security;

import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.security.PreAuthorizePermissionReferenceParser;
import brito.com.multitenancy001.shared.security.PreAuthorizePermissionReferenceParser.EnumConstantRef;
import brito.com.multitenancy001.shared.security.PreAuthorizePermissionReferenceParser.ParsedPermissions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fail-fast: amarra regras de segurança do Tenant.
 *
 * Validações:
 *  1) Nenhuma role tem set vazio (role "decorativa" é bug)
 *  2) Nenhuma permissão fora do escopo TEN_ aparece na matriz role->perms
 *  3) Toda permissão referenciada por @PreAuthorize existe no enum TenantPermission
 *     e não há referências a ControlPlanePermission / CP_ por engano.
 *
 * Liga/desliga:
 *  app.security.tenant.enforce-wiring-verifier=true|false
 *
 * Observação:
 * - Você já tem TenantEndpointPreAuthorizeVerifier (presença de @PreAuthorize).
 * - Este verifier complementa (conteúdo/coerência).
 */
@Component
public class TenantSecurityWiringVerifier implements ApplicationRunner {

    private final RequestMappingHandlerMapping mapping;

    @Value("${app.security.tenant.enforce-wiring-verifier:true}")
    private boolean enabled;

    public TenantSecurityWiringVerifier(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping
    ) {
        this.mapping = mapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        List<String> errors = new ArrayList<>();

        // 1) role -> perms: non-empty + scoped
        validateRoleMatrix(errors);

        // 2) @PreAuthorize references -> must exist + must be TEN scoped
        validatePreAuthorizeReferences(errors);

        if (!errors.isEmpty()) {
            String msg = "TenantSecurityWiringVerifier FAIL-FAST:\n"
                    + errors.stream().distinct().sorted().collect(Collectors.joining("\n"));
            throw new IllegalStateException(msg);
        }
    }

    private void validateRoleMatrix(List<String> errors) {
        for (TenantRole role : TenantRole.values()) {
            Set<TenantPermission> perms;
            try {
                perms = TenantRolePermissions.permissionsFor(role);
            } catch (RuntimeException ex) {
                errors.add("[ROLE_MATRIX] role=" + role + " -> erro ao obter permissões: " + ex.getMessage());
                continue;
            }

            if (perms == null || perms.isEmpty()) {
                errors.add("[ROLE_MATRIX] role=" + role + " -> set de permissões vazio (role decorativa)");
                continue;
            }

            // Escopo TEN_ estrito
            try {
                PermissionScopeValidator.validateTenantPermissionsStrict(perms);
            } catch (RuntimeException ex) {
                errors.add("[ROLE_MATRIX] role=" + role + " -> permissões fora do escopo TEN_: " + ex.getMessage());
            }
        }
    }

    private void validatePreAuthorizeReferences(List<String> errors) {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod hm = e.getValue();

            Class<?> beanType = hm.getBeanType();
            String pkg = beanType.getPackageName();

            // só TENANT
            if (!pkg.startsWith("brito.com.multitenancy001.tenant")) continue;

            Set<String> patterns = extractPatterns(info);
            if (patterns.isEmpty()) continue;

            // mesmo allowlist do seu verifier de presença
            if (isAllowlisted(patterns)) continue;

            Method method = hm.getMethod();

            PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            if (pa == null) {
                pa = AnnotatedElementUtils.findMergedAnnotation(beanType, PreAuthorize.class);
            }

            // se não tiver, o outro verifier já falha
            if (pa == null) continue;

            String expr = pa.value();
            ParsedPermissions parsed = PreAuthorizePermissionReferenceParser.parse(expr);
            if (parsed.isEmpty()) continue;

            String endpoint = describeEndpoint(info, patterns, beanType, method);

            // 2.1) referências via T(Enum).CONST
            for (EnumConstantRef ref : parsed.enumConstantRefs()) {
                String enumFqn = ref.enumFqn();
                String constName = ref.constantName();

                if (!StringUtils.hasText(enumFqn) || !StringUtils.hasText(constName)) continue;

                if (enumFqn.endsWith(".ControlPlanePermission")) {
                    errors.add("[PREAUTH_REF] " + endpoint + " -> referência indevida a ControlPlanePermission: "
                            + enumFqn + "." + constName);
                    continue;
                }

                if (!enumFqn.endsWith(".TenantPermission")) {
                    errors.add("[PREAUTH_REF] " + endpoint + " -> enum inesperado em @PreAuthorize: "
                            + enumFqn + "." + constName + " (esperado TenantPermission)");
                    continue;
                }

                try {
                    TenantPermission.valueOf(constName);
                } catch (IllegalArgumentException ex) {
                    errors.add("[PREAUTH_REF] " + endpoint + " -> permissão inexistente: TenantPermission."
                            + constName + " (expr=" + expr + ")");
                }
            }

            // 2.2) referências por string literal: 'TEN_...' / 'CP_...'
            for (String code : parsed.stringLiteralCodes()) {
                if (!StringUtils.hasText(code)) continue;

                try {
                    PermissionScopeValidator.requireTenantPermission(code);
                } catch (RuntimeException ex) {
                    errors.add("[PREAUTH_REF] " + endpoint + " -> string permission fora do escopo TEN_: "
                            + code + " (" + ex.getMessage() + ")");
                    continue;
                }

                try {
                    TenantPermission.valueOf(code);
                } catch (IllegalArgumentException ex) {
                    errors.add("[PREAUTH_REF] " + endpoint + " -> permissão inexistente (string literal): "
                            + code + " (expr=" + expr + ")");
                }
            }
        }
    }

    // =========================================================
    // helpers (copiados do estilo do seu verifier atual)
    // =========================================================

    private boolean isAllowlisted(Set<String> patterns) {
        for (String p : patterns) {
            if (!StringUtils.hasText(p)) continue;

            if (p.startsWith("/api/tenant/auth")) return true;
            if (p.startsWith("/api/tenant/password")) return true;
            if (p.startsWith("/api/tenant/debug")) return true;
        }
        return false;
    }

    private Set<String> extractPatterns(RequestMappingInfo info) {
        try {
            if (info.getPathPatternsCondition() != null) {
                return new LinkedHashSet<>(info.getPathPatternsCondition().getPatternValues());
            }
        } catch (Throwable ignored) {}

        try {
            if (info.getPatternsCondition() != null) {
                return new LinkedHashSet<>(info.getPatternsCondition().getPatterns());
            }
        } catch (Throwable ignored) {}

        return Set.of();
    }

    private String describeEndpoint(RequestMappingInfo info, Set<String> patterns, Class<?> beanType, Method m) {
        String methods = (info.getMethodsCondition() != null && !info.getMethodsCondition().getMethods().isEmpty())
                ? info.getMethodsCondition().getMethods().toString()
                : "[ANY]";
        return methods + " " + patterns + " -> " + beanType.getSimpleName() + "#" + m.getName();
    }
}
