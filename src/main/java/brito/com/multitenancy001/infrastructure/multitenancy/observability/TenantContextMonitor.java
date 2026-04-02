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
 * Monitor de observabilidade do TenantContext em services.
 *
 * <p>Objetivos:</p>
 * <ul>
 *   <li>Logar tenant bound/effective antes e depois da execução.</li>
 *   <li>Classificar falhas esperadas e inesperadas.</li>
 *   <li>Dar mensagem clara para erro clássico de bind indevido de tenant.</li>
 * </ul>
 */
@Aspect
@Component
@Slf4j
public class TenantContextMonitor {

    @Around("@within(org.springframework.stereotype.Service)")
    public Object monitorServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        String boundTenant = TenantContext.getOrNull();
        String effectiveTenant = TenantContext.getOrDefaultPublic();

        long startTime = System.currentTimeMillis();

        log.debug("🏁 INÍCIO {} | tenant(bound={}, effective={})",
                methodName, boundTenant, effectiveTenant);

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            log.debug("✅ FIM {} ({}ms) | tenant(bound={}, effective={})",
                    methodName, duration, TenantContext.getOrNull(), TenantContext.getOrDefaultPublic());

            return result;

        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;

            if (isTenantContextError(e)) {
                log.warn("🏷️ CONTEXTO_TENANT {} ({}ms) | tenant(bound={}, effective={}) | detalhe: {}",
                        methodName, duration, boundTenant, effectiveTenant, getTenantContextErrorMessage(e));
                throw e;
            }

            if (isInvalidLogin(e)) {
                log.info("🔐 AUTENTICACAO {} ({}ms) | tenant(bound={}, effective={}) | motivo: {}",
                        methodName, duration, boundTenant, effectiveTenant, safeMsg(e));
                throw e;
            }

            ApiException apiEx = findApiException(e);
            if (apiEx != null) {
                logApiException(apiEx, methodName, duration, boundTenant, effectiveTenant, e);
                throw e;
            }

            log.error("❌ ERRO_INESPERADO {} ({}ms) | tenant(bound={}, effective={}) | tipo={} | motivo: {}",
                    methodName, duration, boundTenant, effectiveTenant,
                    e.getClass().getSimpleName(), safeMsg(e), e);
            throw e;
        }
    }

    private boolean isTenantContextError(Throwable e) {
        if (e == null) return false;

        if (e instanceof IllegalStateException) {
            String msg = e.getMessage();
            return msg != null && msg.contains("TenantContext.bindTenantSchema");
        }

        if (e.getCause() != null && e.getCause() != e) {
            return isTenantContextError(e.getCause());
        }

        return false;
    }

    private String getTenantContextErrorMessage(Throwable e) {
        if (e == null) return "Erro desconhecido";

        String msg = e.getMessage();
        if (msg != null && msg.contains("TenantContext.bindTenantSchema")) {
            return "Não é possível mudar o TenantContext dentro do mesmo fluxo já bindado. "
                    + "Revise a cadeia de filtros e evite rebind de tenant diferente no mesmo thread.";
        }

        return safeMsg(e);
    }

    private void logApiException(
            ApiException ex,
            String method,
            long duration,
            String boundTenant,
            String effectiveTenant,
            Throwable original
    ) {
        ApiErrorCategory category = ex.getCategory();
        int status = ex.getStatus();

        String baseLog = "{} {} ({}ms) | tenant(bound={}, effective={}) | status={} código={} | {}";

        if (category == ApiErrorCategory.INTERNAL || status >= 500) {
            log.error(baseLog, "❌ INTERNO", method, duration,
                    boundTenant, effectiveTenant, status, ex.getCode().name(), safeMsg(ex), original);
            return;
        }

        if (category == ApiErrorCategory.AUTH || category == ApiErrorCategory.SECURITY) {
            log.info(baseLog, "🔐 SEGURANCA", method, duration,
                    boundTenant, effectiveTenant, status, ex.getCode().name(), safeMsg(ex));
            return;
        }

        if (category == ApiErrorCategory.VALIDATION || category == ApiErrorCategory.REQUEST) {
            log.warn(baseLog, "⚠️ VALIDACAO", method, duration,
                    boundTenant, effectiveTenant, status, ex.getCode().name(), safeMsg(ex));
            return;
        }

        if (category == ApiErrorCategory.CONFLICT) {
            log.warn(baseLog, "⚡ CONFLITO", method, duration,
                    boundTenant, effectiveTenant, status, ex.getCode().name(), safeMsg(ex));
            return;
        }

        log.warn(baseLog, "📋 NEGOCIO", method, duration,
                boundTenant, effectiveTenant, status, ex.getCode().name(),
                String.format("[%s] %s", category.name(), safeMsg(ex)));
    }

    private ApiException findApiException(Throwable ex) {
        if (ex == null) return null;
        if (ex instanceof ApiException api) return api;
        if (ex.getCause() != null && ex.getCause() != ex) {
            return findApiException(ex.getCause());
        }
        return null;
    }

    private boolean isInvalidLogin(Throwable ex) {
        if (ex == null) return false;

        if (ex instanceof BadCredentialsException) return true;
        if (ex instanceof UsernameNotFoundException) return true;

        if (ex.getCause() != null && ex.getCause() != ex) {
            return isInvalidLogin(ex.getCause());
        }

        return false;
    }

    private String safeMsg(Throwable ex) {
        if (ex == null) return "Exceção nula";
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        if (msg.length() > 150) {
            msg = msg.substring(0, 147) + "...";
        }
        return msg;
    }
}