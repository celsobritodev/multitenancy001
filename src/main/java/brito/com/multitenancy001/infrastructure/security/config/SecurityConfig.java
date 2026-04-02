package brito.com.multitenancy001.infrastructure.security.config;

import brito.com.multitenancy001.infrastructure.security.filter.JwtAuthenticationFilter;
import brito.com.multitenancy001.infrastructure.security.filter.MustChangePasswordFilter;
import brito.com.multitenancy001.infrastructure.security.filter.RequestLoggingFilter;
import brito.com.multitenancy001.infrastructure.security.filter.RequestMetaContextFilter;
import brito.com.multitenancy001.infrastructure.security.filter.TenantHeaderTenantContextFilter;
import brito.com.multitenancy001.infrastructure.security.jwt.JwtTokenProvider;
import brito.com.multitenancy001.infrastructure.security.userdetails.MultiContextUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

/**
 * Configuração central de segurança da aplicação.
 *
 * <p>Diretrizes desta versão endurecida:</p>
 * <ul>
 *   <li>Fluxo 100% stateless com JWT.</li>
 *   <li>Contexto de request e contexto de tenant separados por filtro dedicado.</li>
 *   <li>JwtAuthenticationFilter não acessa repository tenant diretamente.</li>
 *   <li>Validação do principal tenant ocorre via MultiContextUserDetailsService já com TenantContext bindado.</li>
 * </ul>
 *
 * <p>Ordem de filtros:</p>
 * <ol>
 *   <li>RequestMetaContextFilter</li>
 *   <li>TenantHeaderTenantContextFilter</li>
 *   <li>JwtAuthenticationFilter</li>
 *   <li>MustChangePasswordFilter</li>
 *   <li>RequestLoggingFilter</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final MultiContextUserDetailsService multiContextUserDetailsService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    /**
     * Filtro JWT principal.
     *
     * @return bean do filtro JWT
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(
                jwtTokenProvider,
                multiContextUserDetailsService,
                restAuthenticationEntryPoint,
                restAccessDeniedHandler
        );
    }

    /**
     * Filtro responsável por propagar dados do request para contexto local.
     *
     * @return bean do filtro
     */
    @Bean
    public RequestMetaContextFilter requestMetaContextFilter() {
        return new RequestMetaContextFilter();
    }

    /**
     * Filtro responsável por binding do tenant antes do JWT.
     *
     * @return bean do filtro
     */
    @Bean
    public TenantHeaderTenantContextFilter tenantHeaderTenantContextFilter() {
        return new TenantHeaderTenantContextFilter();
    }

    /**
     * Filtro da política de troca obrigatória de senha.
     *
     * @return bean do filtro
     */
    @Bean
    public MustChangePasswordFilter mustChangePasswordFilter() {
        return new MustChangePasswordFilter();
    }

    /**
     * Filtro de logging final da request.
     *
     * @return bean do filtro
     */
    @Bean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    /**
     * Encoder padrão.
     *
     * @return PasswordEncoder BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager padrão do Spring Security.
     *
     * @param authConfig configuração
     * @return manager
     * @throws Exception erro de wiring
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Cadeia principal de filtros HTTP.
     *
     * @param http builder
     * @return security filter chain
     * @throws Exception erro de configuração
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOriginPatterns(List.of(
                            "http://localhost:*",
                            "http://127.0.0.1:*"
                    ));
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of(
                            "Authorization",
                            "Content-Type",
                            "Accept",
                            "X-Request-Id",
                            "X-Tenant"
                    ));
                    config.setExposedHeaders(List.of(
                            "Authorization",
                            "X-Request-Id"
                    ));
                    config.setAllowCredentials(true);
                    config.setMaxAge(3600L);
                    return config;
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/actuator/health",

                                "/api/controlplane/auth/login",
                                "/api/controlplane/auth/refresh",
                                "/api/controlplane/auth/logout",

                                "/api/tenant/auth/login",
                                "/api/tenant/auth/login/confirm",
                                "/api/tenant/auth/refresh",
                                "/api/tenant/auth/logout",

                                "/api/tenant/me/password",
                                "/api/tenant/password/forgot",
                                "/api/tenant/password/reset",

                                "/api/accounts/auth/checkuser",
                                "/api/signup"
                        ).permitAll()

                        .requestMatchers("/api/admin/me/password").authenticated()
                        .requestMatchers("/api/me/**").authenticated()
                        .requestMatchers("/api/admin/**").authenticated()
                        .requestMatchers("/api/controlplane/**").authenticated()
                        .requestMatchers("/api/tenant/**").authenticated()

                        .anyRequest().denyAll()
                );

        http.addFilterBefore(requestMetaContextFilter(), UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(tenantHeaderTenantContextFilter(), RequestMetaContextFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter(), TenantHeaderTenantContextFilter.class);
        http.addFilterAfter(mustChangePasswordFilter(), JwtAuthenticationFilter.class);
        http.addFilterAfter(requestLoggingFilter(), MustChangePasswordFilter.class);

        return http.build();
    }
}