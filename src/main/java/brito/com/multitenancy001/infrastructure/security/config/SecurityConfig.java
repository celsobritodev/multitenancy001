package brito.com.multitenancy001.infrastructure.security.config;

import brito.com.multitenancy001.infrastructure.security.filter.JwtAuthenticationFilter;
import brito.com.multitenancy001.infrastructure.security.filter.MustChangePasswordFilter;
import brito.com.multitenancy001.infrastructure.security.filter.RequestLoggingFilter;
import brito.com.multitenancy001.infrastructure.security.filter.RequestMetaContextFilter;
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
 * Configuração de segurança (JWT stateless).
 *
 * Ordem de filtros:
 * 1) RequestMetaContextFilter (correlation id / ip / user-agent)
 * 2) JwtAuthenticationFilter (autenticação JWT)
 * 3) MustChangePasswordFilter (política 428)
 * 4) RequestLoggingFilter (log final)
 *
 * Ajuste:
 * - libera /api/tenant/auth/logout e /api/controlplane/auth/logout como public endpoints
 *   (logout é baseado em refreshToken no body, e não depende do access token).
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

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(
                jwtTokenProvider,
                multiContextUserDetailsService,
                restAuthenticationEntryPoint,
                restAccessDeniedHandler
        );
    }

    @Bean
    public MustChangePasswordFilter mustChangePasswordFilter() {
        return new MustChangePasswordFilter();
    }

    @Bean
    public RequestMetaContextFilter requestMetaContextFilter() {
        return new RequestMetaContextFilter();
    }

    @Bean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())

            // ✅ CORS (opcional, mas recomendado) + X-Request-Id
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
                    .authenticationEntryPoint(restAuthenticationEntryPoint) // 401
                    .accessDeniedHandler(restAccessDeniedHandler)           // 403
            )
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/actuator/health",

                    // ✅ CONTROL PLANE AUTH
                    "/api/controlplane/auth/login",
                    "/api/controlplane/auth/refresh",
                    "/api/controlplane/auth/logout",

                    // ✅ TENANT AUTH
                    "/api/tenant/auth/login",
                    "/api/tenant/auth/login/confirm",
                    "/api/tenant/auth/refresh",
                    "/api/tenant/auth/logout",

                    // ✅ TENANT: troca de senha (JWT, mas precisa passar pelo filtro 428)
                    // OBS: Security precisa deixar chegar no controller; o MustChangePasswordFilter decide 428 vs allow.
                    "/api/tenant/me/password",

                    // ✅ reset por token (público)
                    "/api/tenant/password/forgot",
                    "/api/tenant/password/reset",

                    "/api/accounts/auth/checkuser",
                    "/api/signup"
                ).permitAll()

                // rotas autenticadas
                .requestMatchers("/api/admin/me/password").authenticated()
                .requestMatchers("/api/me/**").authenticated()

                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/api/controlplane/**").authenticated()
                .requestMatchers("/api/tenant/**").authenticated()

                .anyRequest().denyAll()
            );

        // ✅ 1) meta primeiro
        http.addFilterBefore(requestMetaContextFilter(), UsernamePasswordAuthenticationFilter.class);

        // ✅ 2) JWT cedo
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        // ✅ 3) política de senha
        http.addFilterAfter(mustChangePasswordFilter(), JwtAuthenticationFilter.class);

        // ✅ 4) log final do request
        http.addFilterAfter(requestLoggingFilter(), MustChangePasswordFilter.class);

        return http.build();
    }
}
