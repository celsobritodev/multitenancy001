package brito.com.multitenancy001.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.exceptions.ApiException;
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

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		System.out.println("=== JWT FILTER DEBUG ===");
		System.out.println("URI: " + request.getRequestURI());

		if (shouldNotFilter(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			String jwt = getJwtFromRequest(request);

			if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {

				String username = tokenProvider.getUsernameFromToken(jwt);
				String tokenType = tokenProvider.getTokenType(jwt); // PLATFORM | TENANT

				// 🔥 DECISÃO CRÍTICA
				if ("PLATFORM".equals(tokenType)) {

					// 👉 SUPER_ADMIN SEMPRE NO PUBLIC
					TenantContext.clear();

				} else {

					// 👉 USUÁRIO DE TENANT
					String tenantSchema = tokenProvider.getTenantSchemaFromToken(jwt);

					if (!StringUtils.hasText(tenantSchema)) {
						throw new ApiException(
								"TENANT_NOT_DEFINED",
								"Tenant não definido no token",
								401
						);
					}

					TenantContext.setCurrentTenant(tenantSchema);
				}

				UserDetails userDetails = userDetailsService.loadUserByUsername(username);

				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						userDetails, null, userDetails.getAuthorities());

				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

				SecurityContextHolder.getContext().setAuthentication(authentication);
			}

		} catch (Exception ex) {
			TenantContext.clear(); // ⭐ OBRIGATÓRIO
			SecurityContextHolder.clearContext();
			logger.error("Erro no filtro JWT", ex);
		}

		filterChain.doFilter(request, response);
	}

	// ⭐⭐ MÉTODO NOVO: Determina quando NÃO aplicar o filtro ⭐⭐
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
	    String requestURI = request.getRequestURI();

	    return requestURI.startsWith("/api/auth") ||
	           requestURI.startsWith("/api/accounts/auth") ||
	           requestURI.startsWith("/api/admin/auth") || // 🔥 AQUI
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