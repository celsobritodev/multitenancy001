package brito.com.multitenancy001.infrastructure.multitenancy.observability;

import brito.com.multitenancy001.shared.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class TenantContextMonitor {

    @Around("@within(org.springframework.stereotype.Service)")
    public Object monitorServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();

        String boundTenant = TenantContext.getOrNull();               // null ou tenant real
        String effectiveTenant = TenantContext.getOrDefaultPublic();  // tenant ou public/controlplane

        log.debug("🏁 INÍCIO {} - Tenant(bound={}, effective={})", methodName, boundTenant, effectiveTenant);

        try {
            Object result = joinPoint.proceed();

            String boundAfter = TenantContext.getOrNull();
            String effectiveAfter = TenantContext.getOrDefaultPublic();

            log.debug("✅ FIM {} - Tenant(bound={}, effective={})", methodName, boundAfter, effectiveAfter);
            return result;

        } catch (Throwable e) {

            // ✅ Login inválido = fluxo normal (não é erro do sistema)
            if (isInvalidLogin(e)) {
                log.info("🔐 AUTH inválida {} - Tenant(bound={}, effective={}) - {}",
                        methodName, boundTenant, effectiveTenant, safeMsg(e));
                throw e;
            }

            // ❌ Erro real
            log.error("❌ ERRO {} - Tenant(bound={}, effective={}) - Erro: {}",
                    methodName, boundTenant, effectiveTenant, safeMsg(e), e);
            throw e;
        }
    }

    private boolean isInvalidLogin(Throwable ex) {
        if (ex == null) return false;

        // Spring Security (senha errada / user inexistente após map)
        if (ex instanceof BadCredentialsException) return true;

        // user inexistente no UserDetailsService
        if (ex instanceof UsernameNotFoundException unf) {
            return "INVALID_USER".equalsIgnoreCase(unf.getMessage());
        }

        // encadeado (muito comum virem wrapped)
        Throwable cause = ex.getCause();
        if (cause != null && cause != ex) return isInvalidLogin(cause);

        return false;
    }

    private String safeMsg(Throwable ex) {
        String msg = ex.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : ex.getClass().getSimpleName();
    }
}


