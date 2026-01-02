package brito.com.multitenancy001.security;

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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig
    ) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> authz

                // 🔓 ACTUATOR HEALTH (PUBLIC)
                .requestMatchers("/actuator/health").permitAll()

                // 🔓 LOGIN / REFRESH (PLATFORM)
                .requestMatchers(
                    "/api/admin/auth/login",
                    "/api/admin/auth/refresh"
                ).permitAll()

                // 🔓 LOGIN / REFRESH (TENANT)
                .requestMatchers(
                		"/api/tenant/auth/login",
                    "/api/auth/refresh",
                    "/api/accounts/auth/checkuser",
                    "/api/tenant/auth/forgot-password",
                    "/api/tenant/auth/reset-password",
                    "/api/signup"
                ).permitAll()

                // 🔒 PLATFORM (APÓS LOGIN)
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")

                .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
