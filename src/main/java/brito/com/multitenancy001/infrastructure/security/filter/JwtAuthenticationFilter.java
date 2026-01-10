package brito.com.multitenancy001.infrastructure.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.security.userdetails.MultiContextUserDetailsService;
import brito.com.multitenancy001.shared.context.TenantContext;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final MultiContextUserDetailsService multiContextUserDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        boolean bound = false;

        try {
            final String authHeader = request.getHeader("Authorization");

            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            final String jwt = authHeader.substring(7);

            if (!jwtTokenProvider.validateToken(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            final String tokenType = jwtTokenProvider.getTokenType(jwt);
            final String username = jwtTokenProvider.getUsernameFromToken(jwt);
            
            if (!"TENANT".equals(tokenType) && !"CONTROLPLANE".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }


            if (!StringUtils.hasText(username)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails;

                if ("TENANT".equals(tokenType)) {
                    final String tenantSchema = jwtTokenProvider.getTenantSchemaFromToken(jwt);

                    if (!StringUtils.hasText(tenantSchema) || "public".equalsIgnoreCase(tenantSchema)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    
                    if (!tenantSchema.matches("^[a-zA-Z0-9_]+$")) {
                        filterChain.doFilter(request, response);
                        return;
                    }


                    TenantContext.bind(tenantSchema);
                    bound = true;

                    Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
                    if (accountId == null) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    userDetails = multiContextUserDetailsService.loadTenantUser(username, accountId);

                } else if ("CONTROLPLANE".equals(tokenType)) {
                	
                	String context = jwtTokenProvider.getContextFromToken(jwt);
                	if (StringUtils.hasText(context) && !"public".equalsIgnoreCase(context)) {
                	    filterChain.doFilter(request, response);
                	    return;
                	}


                	Long accountId = jwtTokenProvider.getAccountIdFromToken(jwt);
                	if (accountId == null) {
                	    filterChain.doFilter(request, response);
                	    return;
                	}

                	userDetails = multiContextUserDetailsService.loadControlPlaneUser(username, accountId);


                } else {
                    // REFRESH / PASSWORD_RESET etc: normalmente não autentica request padrão
                    filterChain.doFilter(request, response);
                    return;
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(request, response);

        } finally {
            if (bound) {
                TenantContext.clear();
            }
        }
    }
}
