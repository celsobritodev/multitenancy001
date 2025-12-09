package brito.com.example.multitenancy001.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import brito.com.example.multitenancy001.configuration.TenantContext;
import brito.com.example.multitenancy001.services.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Autowired
    private AccountService accountService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        String token = extractToken(request);
        
        if (token != null && tokenProvider.validateToken(token)) {
            String username = tokenProvider.getUsernameFromToken(token);
            // Buscar conta do usuário e setar schema
            
         // Busca o schema do tenant pelo username
            String schemaName = accountService.findSchemaByUsername(username);
            
            
         // Seta o schema atual do Hibernate
            TenantContext.setCurrentTenant(schemaName);
        }
        
        return true;
    }
    
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}