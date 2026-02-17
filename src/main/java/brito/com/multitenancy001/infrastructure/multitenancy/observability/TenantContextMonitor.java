package brito.com.multitenancy001.infrastructure.multitenancy.observability;

import brito.com.multitenancy001.shared.api.error.ApiErrorCategory;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Observabilidade do TenantContext em execução de Services.
 *
 * Responsabilidades:
 * - Logar tenant bound/effective
 * - Classificar falhas (AUTH, VALIDATION, BUSINESS, INTERNAL)
 * - Evitar stacktrace para fluxo esperado
 *
 * Não altera fluxo de exceções — apenas observa.
 */
@Aspect
@Component
@Slf4j
public class TenantContextMonitor {

    @Around("@within(org.springframework.stereotype.Service)")
    public Object monitorServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        /* Envolve métodos de Service para logging contextual e semântico. */
        String methodName = joinPoint.getSignature().toShortString();

        String boundTenant = TenantContext.getOrNull();
        String effectiveTenant = TenantContext.getOrDefaultPublic();

        log.debug("🏁 INÍCIO {} | tenant(bound={}, effective={})",
                methodName, boundTenant, effectiveTenant);

        try {
            Object result = joinPoint.proceed();

            log.debug("✅ FIM {} | tenant(bound={}, effective={})",
                    methodName,
                    TenantContext.getOrNull(),
                    TenantContext.getOrDefaultPublic());

            return result;

        } catch (Throwable e) {

            /* ===============================
               AUTH inválida = fluxo normal
               =============================== */
            if (isInvalidLogin(e)) {
                log.info("🔐 AUTH {} | tenant(bound={}, effective={}) | msg={}",
                        methodName, boundTenant, effectiveTenant, safeMsg(e));
                throw e;
            }

            ApiException apiEx = findApiException(e);
            if (apiEx != null) {
                logApiException(apiEx, methodName, boundTenant, effectiveTenant, e);
                throw e;
            }

            /* ===============================
               Erro inesperado
               =============================== */
            log.error("❌ ERROR {} | tenant(bound={}, effective={}) | msg={}",
                    methodName, boundTenant, effectiveTenant, safeMsg(e), e);
            throw e;
        }
    }

    private void logApiException(
            ApiException ex,
            String method,
            String boundTenant,
            String effectiveTenant,
            Throwable original
    ) {
        /* Classifica ApiException conforme categoria e status HTTP. */
        ApiErrorCategory category = ex.getCategory();
        int status = ex.getStatus();

        // INTERNAL = erro real
        if (category == ApiErrorCategory.INTERNAL || status >= 500) {
            log.error("❌ INTERNAL {} | tenant(bound={}, effective={}) | status={} code={} msg={}",
                    method,
                    boundTenant,
                    effectiveTenant,
                    status,
                    ex.getCode().name(),
                    safeMsg(ex),
                    original
            );
            return;
        }

        // AUTH / SECURITY
        if (category == ApiErrorCategory.AUTH || category == ApiErrorCategory.SECURITY) {
            log.info("🔐 AUTH {} | tenant(bound={}, effective={}) | status={} code={} msg={}",
                    method,
                    boundTenant,
                    effectiveTenant,
                    status,
                    ex.getCode().name(),
                    safeMsg(ex)
            );
            return;
        }

        // VALIDATION / REQUEST
        if (category == ApiErrorCategory.VALIDATION || category == ApiErrorCategory.REQUEST) {
            log.warn("⚠️ VALIDATION {} | tenant(bound={}, effective={}) | status={} code={} msg={}",
                    method,
                    boundTenant,
                    effectiveTenant,
                    status,
                    ex.getCode().name(),
                    safeMsg(ex)
            );
            return;
        }

        // CONFLICT
        if (category == ApiErrorCategory.CONFLICT) {
            log.warn("⚠️ CONFLICT {} | tenant(bound={}, effective={}) | status={} code={} msg={}",
                    method,
                    boundTenant,
                    effectiveTenant,
                    status,
                    ex.getCode().name(),
                    safeMsg(ex)
            );
            return;
        }

        // Regra de negócio (CATEGORIES, PRODUCTS, etc.)
        log.warn("⚠️ BUSINESS {} | tenant(bound={}, effective={}) | status={} code={} category={} msg={}",
                method,
                boundTenant,
                effectiveTenant,
                status,
                ex.getCode().name(),
                category.name(),
                safeMsg(ex)
        );
    }

    private ApiException findApiException(Throwable ex) {
        /* Busca ApiException na cadeia de causas. */
        if (ex == null) return null;
        if (ex instanceof ApiException api) return api;
        if (ex.getCause() != null && ex.getCause() != ex) {
            return findApiException(ex.getCause());
        }
        return null;
    }

    private boolean isInvalidLogin(Throwable ex) {
        /* Mantém compatibilidade com Spring Security. */
        if (ex == null) return false;

        if (ex instanceof BadCredentialsException) return true;

        if (ex instanceof UsernameNotFoundException unf) {
            return "INVALID_USER".equalsIgnoreCase(unf.getMessage());
        }

        if (ex.getCause() != null && ex.getCause() != ex) {
            return isInvalidLogin(ex.getCause());
        }

        return false;
    }

    private String safeMsg(Throwable ex) {
        /* Garante mensagem sempre legível. */
        String msg = ex.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : ex.getClass().getSimpleName();
    }
}
