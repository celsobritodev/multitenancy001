package brito.com.multitenancy001.controlplane.security;

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
 * Fail-fast: amarra regras de segurança do Control Plane.
 *
 * Validações:
 *  1) Nenhuma role tem set vazio (role "decorativa" é bug)
 *  2) Nenhuma permissão fora do escopo CP_ aparece na matriz role->perms
 *  3) Toda permissão referenciada por @PreAuthorize existe no enum ControlPlanePermission
 *     e não há referências a TenantPermission / TEN_ por engano.
 *
 * Liga/desliga:
 *  app.security.controlplane.enforce-wiring-verifier=true|false
 *
 * Observação:
 * - Você já tem ControlPlaneEndpointPreAuthorizeVerifier (presença de @PreAuthorize).
 * - Este verifier NÃO substitui; ele complementa (conteúdo/coerência).
 */
@Component
public class ControlPlaneSecurityWiringVerifier implements ApplicationRunner {

    private final RequestMappingHandlerMapping mapping;

    @Value("${app.security.controlplane.enforce-wiring-verifier:true}")
    private boolean enabled;

    public ControlPlaneSecurityWiringVerifier(
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

        // 2) @PreAuthorize references -> must exist + must be CP scoped
        validatePreAuthorizeReferences(errors);

        if (!errors.isEmpty()) {
            String msg = "ControlPlaneSecurityWiringVerifier FAIL-FAST:\n"
                    + errors.stream().distinct().sorted().collect(Collectors.joining("\n"));
            throw new IllegalStateException(msg);
        }
    }

    private void validateRoleMatrix(List<String> errors) {
        for (ControlPlaneRole role : ControlPlaneRole.values()) {
            Set<ControlPlanePermission> perms;
            try {
                perms = ControlPlaneRolePermissions.permissionsFor(role);
            } catch (RuntimeException ex) {
                errors.add("[ROLE_MATRIX] role=" + role + " -> erro ao obter permissões: " + ex.getMessage());
                continue;
            }

            if (perms == null || perms.isEmpty()) {
                errors.add("[ROLE_MATRIX] role=" + role + " -> set de permissões vazio (role decorativa)");
                continue;
            }

            // Escopo CP_ estrito (fail-fast via shared)
            try {
                PermissionScopeValidator.validateControlPlanePermissionsStrict(perms);
            } catch (RuntimeException ex) {
                errors.add("[ROLE_MATRIX] role=" + role + " -> permissões fora do escopo CP_: " + ex.getMessage());
            }
        }
    }

    private void validatePreAuthorizeReferences(List<String> errors) {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod hm = e.getValue();

            Class<?> beanType = hm.getBeanType();
            String pkg = beanType.getPackageName();

            // só Control Plane
            if (!pkg.startsWith("brito.com.multitenancy001.controlplane")) continue;

            Set<String> patterns = extractPatterns(info);
            if (patterns.isEmpty()) continue;

            // mesmo allowlist do seu verifier de presença
            if (isAllowlisted(patterns)) continue;

            Method method = hm.getMethod();

            // pega o @PreAuthorize do método; se não tiver, tenta no tipo (classe)
            PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            if (pa == null) {
                pa = AnnotatedElementUtils.findMergedAnnotation(beanType, PreAuthorize.class);
            }

            // Se não tiver, o outro verifier já vai falhar; aqui não duplicamos.
            if (pa == null) continue;

            String expr = pa.value();
            ParsedPermissions parsed = PreAuthorizePermissionReferenceParser.parse(expr);

            // Se a expressão não referenciar nenhuma permissão explícita,
            // não dá para validar existência (ex.: "isAuthenticated()").
            if (parsed.isEmpty()) continue;

            String endpoint = describeEndpoint(info, patterns, beanType, method);

            // 2.1) referências via T(Enum).CONST
            for (EnumConstantRef ref : parsed.enumConstantRefs()) {
                String enumFqn = ref.enumFqn();
                String constName = ref.constantName();

                if (!StringUtils.hasText(enumFqn) || !StringUtils.hasText(constName)) continue;

                // Se apontou pro enum errado (TenantPermission), é vazamento cross-context
                if (enumFqn.endsWith(".TenantPermission")) {
                    errors.add("[PREAUTH_REF] " + endpoint + " -> referência indevida a TenantPermission: "
                            + enumFqn + "." + constName);
                    continue;
                }

                // Esperado: ControlPlanePermission
                if (!enumFqn.endsWith(".ControlPlanePermission")) {
                    // pode ser outra estratégia, mas em geral aqui é bug/noise
                    errors.add("[PREAUTH_REF] " + endpoint + " -> enum inesperado em @PreAuthorize: "
                            + enumFqn + "." + constName + " (esperado ControlPlanePermission)");
                    continue;
                }

                // Existe no enum?
                try {
                    ControlPlanePermission.valueOf(constName);
                } catch (IllegalArgumentException ex) {
                    errors.add("[PREAUTH_REF] " + endpoint + " -> permissão inexistente: ControlPlanePermission."
                            + constName + " (expr=" + expr + ")");
                }
            }

          // 2.2) referências por string literal: 'CP_...' / 'TEN_...'
for (String code : parsed.stringLiteralCodes()) {
    if (!StringUtils.hasText(code)) continue;

    // POLÍTICA: string literal é proibido (use enum + .asAuthority()).
    errors.add("[PREAUTH_LITERAL] " + endpoint + " -> uso de string literal em @PreAuthorize é proibido: '"
            + code + "' (expr=" + expr + ")");
}
        }
    }

    // =========================================================
    // helpers (copiados do estilo do seu verifier atual)
    // =========================================================

    private boolean isAllowlisted(Set<String> patterns) {
        for (String p : patterns) {
            if (!StringUtils.hasText(p)) continue;

            if (p.startsWith("/api/controlplane/auth")) return true;
            if (p.startsWith("/api/signup")) return true;
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
