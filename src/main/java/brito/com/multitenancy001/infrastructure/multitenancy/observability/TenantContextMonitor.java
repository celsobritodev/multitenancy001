package brito.com.multitenancy001.infrastructure.multitenancy.observability;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.infrastructure.multitenancy.SchemaContext;

@Aspect
@Component
@Slf4j
public class TenantContextMonitor {
    
    @Around("@within(org.springframework.stereotype.Service)")
    public Object monitorServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        String currentTenant = SchemaContext.getCurrentSchema();
        
        log.debug("🏁 INÍCIO {} - Tenant: {}", methodName, currentTenant);
        
        try {
            Object result = joinPoint.proceed();
            log.debug("✅ FIM {} - Tenant: {}", methodName, SchemaContext.getCurrentSchema());
            return result;
        } catch (Exception e) {
            log.error("❌ ERRO {} - Tenant: {} - Erro: {}", 
                     methodName, currentTenant, e.getMessage());
            throw e;
        }
    }
}