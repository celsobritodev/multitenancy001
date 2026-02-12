package brito.com.multitenancy001.tenant.security;

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
 * Fail-fast: garante que endpoints do TENANT tenham @PreAuthorize,
 * exceto os explicitamente allowlisted (login/refresh/reset etc.).
 *
 * Desative em testes/local se quiser:
 *   app.security.tenant.enforce-preauthorize=false
 */
@Component
public class TenantEndpointPreAuthorizeVerifier implements ApplicationRunner {

    private final RequestMappingHandlerMapping mapping;

    @Value("${app.security.tenant.enforce-preauthorize:true}")
    private boolean enabled;

    public TenantEndpointPreAuthorizeVerifier(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping
    ) {
        this.mapping = mapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        List<String> missing = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod hm = e.getValue();

            Class<?> beanType = hm.getBeanType();
            String pkg = beanType.getPackageName();

            // só TENANT
            if (!pkg.startsWith("brito.com.multitenancy001.tenant")) continue;

            Set<String> patterns = extractPatterns(info);
            if (patterns.isEmpty()) continue;

            // allowlist de endpoints públicos / especiais
            if (isAllowlisted(patterns)) continue;

            Method m = hm.getMethod();
            boolean has = hasPreAuthorize(beanType) || hasPreAuthorize(m);

            if (!has) {
                String methods = (info.getMethodsCondition() != null && !info.getMethodsCondition().getMethods().isEmpty())
                        ? info.getMethodsCondition().getMethods().toString()
                        : "[ANY]";

                String line = methods + " " + patterns + " -> " + beanType.getSimpleName() + "#" + m.getName();
                missing.add(line);
            }
        }

        if (!missing.isEmpty()) {
            String msg = "Endpoints do TENANT sem @PreAuthorize (fail-fast):\n"
                    + missing.stream().sorted().collect(Collectors.joining("\n"));
            throw new IllegalStateException(msg);
        }
    }

    private boolean hasPreAuthorize(Class<?> type) {
        return AnnotatedElementUtils.hasAnnotation(type, PreAuthorize.class);
    }

    private boolean hasPreAuthorize(Method m) {
        return AnnotatedElementUtils.hasAnnotation(m, PreAuthorize.class);
    }

    private boolean isAllowlisted(Set<String> patterns) {
        for (String p : patterns) {
            if (!StringUtils.hasText(p)) continue;

            // auth do tenant (login init/confirm/refresh)
            if (p.startsWith("/api/tenant/auth")) return true;

            // reset/forgot password
            if (p.startsWith("/api/tenant/password")) return true;

            // debug em dev (seu controller está @Profile("dev"))
            if (p.startsWith("/api/tenant/debug")) return true;
        }
        return false;
    }

    private Set<String> extractPatterns(RequestMappingInfo info) {
        try {
            // Boot 3 com PathPatterns
            if (info.getPathPatternsCondition() != null) {
                return new LinkedHashSet<>(info.getPathPatternsCondition().getPatternValues());
            }
        } catch (Throwable ignored) {}

        try {
            // fallback (ant style)
            if (info.getPatternsCondition() != null) {
                return new LinkedHashSet<>(info.getPatternsCondition().getPatterns());
            }
        } catch (Throwable ignored) {}

        return Set.of();
    }
}
