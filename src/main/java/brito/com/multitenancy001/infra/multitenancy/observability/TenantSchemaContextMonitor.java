package brito.com.multitenancy001.infra.multitenancy.observability;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.infra.multitenancy.TenantSchemaContext;

@Aspect
@Component
@Slf4j
public class TenantSchemaContextMonitor {
    
    @Around("@within(org.springframework.stereotype.Service)")
    public Object monitorServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        String currentTenant = TenantSchemaContext.getCurrentTenantSchema();
        
        log.debug("🏁 INÍCIO {} - Tenant: {}", methodName, currentTenant);
        
        try {
            Object result = joinPoint.proceed();
            log.debug("✅ FIM {} - Tenant: {}", methodName, TenantSchemaContext.getCurrentTenantSchema());
            return result;
        } catch (Exception e) {
            log.error("❌ ERRO {} - Tenant: {} - Erro: {}", 
                     methodName, currentTenant, e.getMessage());
            throw e;
        }
    }
}