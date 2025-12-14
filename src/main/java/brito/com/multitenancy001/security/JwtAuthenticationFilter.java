package brito.com.multitenancy001.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.services.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Autowired
    private CustomUserDetailsService userDetailsService;
    
    // ⬇️⬇️⬇️ SUBSTITUA O MÉTODO doFilterInternal ATUAL POR ESTE: ⬇️⬇️⬇️
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        
        System.out.println("=== JWT FILTER DEBUG ===");
        System.out.println("URI: " + request.getRequestURI());
        System.out.println("shouldNotFilter? " + shouldNotFilter(request));
        
        // ⭐⭐ VERIFICAÇÃO CRÍTICA: Ignorar endpoints públicos ⭐⭐
        if (shouldNotFilter(request)) {
            System.out.println("✅ Ignorando filtro (endpoint público)");
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            String jwt = getJwtFromRequest(request);
            System.out.println("JWT encontrado? " + (jwt != null));
            
            if (StringUtils.hasText(jwt)) {
                System.out.println("JWT válido? " + tokenProvider.validateToken(jwt));
                
                if (tokenProvider.validateToken(jwt)) {
                    String username = tokenProvider.getUsernameFromToken(jwt);
                    String tenantSchema = tokenProvider.getTenantSchemaFromToken(jwt);
                    System.out.println("Username: " + username);
                    System.out.println("Tenant: " + tenantSchema);
                    
                    // Setar tenant no contexto
                    TenantContext.setCurrentTenant(tenantSchema);
                    
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    System.out.println("✅ Autenticação configurada com sucesso!");
                } else {
                    System.out.println("❌ Token inválido!");
                    // Não limpa o contexto para ver se outras regras permitem
                }
            } else {
                System.out.println("❌ Sem token no header!");
            }
        } catch (Exception ex) {
            System.out.println("❌ ERRO no filtro: " + ex.getMessage());
            ex.printStackTrace();
            logger.error("Could not set user authentication in security context", ex);
        }
        
        filterChain.doFilter(request, response);
   
    
    }
    
    
    
    
    
    
    // ⭐⭐ MÉTODO NOVO: Determina quando NÃO aplicar o filtro ⭐⭐
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        
        // Deve corresponder EXATAMENTE ao que está em SecurityConfig
        return requestURI.equals("/api/accounts") ||
               requestURI.equals("/api/accounts/auth/checkuser") ||
               requestURI.equals("/api/accounts/auth/forgot-password") ||
               requestURI.equals("/api/accounts/auth/reset-password") ||
               request.getMethod().equals("OPTIONS");
    }
    
    
    
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}