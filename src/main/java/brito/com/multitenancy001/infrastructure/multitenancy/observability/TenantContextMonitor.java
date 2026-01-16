package brito.com.multitenancy001.infrastructure.multitenancy.observability;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.TenantContext;

@Aspect
@Component
@Slf4j
public class TenantContextMonitor {
    
	@Around("@within(org.springframework.stereotype.Service)")
	public Object monitorServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
	    String methodName = joinPoint.getSignature().toShortString();

	    String boundTenant = TenantContext.getOrNull();               // null ou tenant real
	    String effectiveTenant = TenantContext.getOrDefaultPublic();  // tenant ou "public"

	    log.debug("🏁 INÍCIO {} - Tenant(bound={}, effective={})", methodName, boundTenant, effectiveTenant);

	    try {
	        Object result = joinPoint.proceed();

	        String boundAfter = TenantContext.getOrNull();
	        String effectiveAfter = TenantContext.getOrDefaultPublic();

	        log.debug("✅ FIM {} - Tenant(bound={}, effective={})", methodName, boundAfter, effectiveAfter);
	        return result;

	    } catch (Exception e) {
	        log.error("❌ ERRO {} - Tenant(bound={}, effective={}) - Erro: {}",
	                methodName, boundTenant, effectiveTenant, e.getMessage());
	        throw e;
	    }
	}

}