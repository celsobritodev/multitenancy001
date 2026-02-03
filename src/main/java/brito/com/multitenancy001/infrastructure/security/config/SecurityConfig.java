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

                // ajuste conforme seu front
                config.setAllowedOriginPatterns(List.of(
                        "http://localhost:*",
                        "http://127.0.0.1:*"
                ));

                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

                // ✅ permite o cliente enviar X-Request-Id + X-Tenant
                config.setAllowedHeaders(List.of(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "X-Request-Id",
                        "X-Tenant"
                ));

                // ✅ expõe X-Request-Id para leitura no front
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
                    "/api/admin/auth/login",
                    "/api/admin/auth/refresh",
                    "/api/tenant/auth/login",
                    "/api/tenant/auth/login/confirm",
                    "/api/tenant/auth/refresh",
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

        // ✅ 1) meta primeiro (requestId/ip/ua) + cleanup centralizado
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
