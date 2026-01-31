package brito.com.multitenancy001.infrastructure.security.config;

import brito.com.multitenancy001.infrastructure.security.filter.JwtAuthenticationFilter;
import brito.com.multitenancy001.infrastructure.security.filter.MustChangePasswordFilter;
import brito.com.multitenancy001.infrastructure.security.filter.RequestLoggingFilter;
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
                restAuthenticationEntryPoint
        );
    }

    @Bean
    public MustChangePasswordFilter mustChangePasswordFilter() {
        return new MustChangePasswordFilter();
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
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ✅ aqui está a correção de 401 vs 403
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

        // ✅ 1) JWT cedo: se token é inválido => 401 (entryPoint)
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        // ✅ 2) MustChangePassword depois do JWT
        http.addFilterAfter(mustChangePasswordFilter(), JwtAuthenticationFilter.class);

        // ✅ 3) Log no final (já com tenant resolvido)
        http.addFilterAfter(requestLoggingFilter(), MustChangePasswordFilter.class);

        return http.build();
    }
}
