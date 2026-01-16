package brito.com.multitenancy001.infrastructure.security.config;

import brito.com.multitenancy001.infrastructure.security.filter.JwtAuthenticationFilter;
import brito.com.multitenancy001.infrastructure.security.filter.MustChangePasswordFilter;
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
    public MustChangePasswordFilter mustChangePasswordFilter() {
        return new MustChangePasswordFilter();
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
            .authorizeHttpRequests(authz -> authz

                // =========================
                // 🔓 PUBLIC
                // =========================
                .requestMatchers("/actuator/health").permitAll()

                // =========================
                // 🔓 AUTH CONTROLPLANE (admin)
                // =========================
                .requestMatchers(
                    "/api/admin/auth/login",
                    "/api/admin/auth/refresh"
                ).permitAll()

                // ✅ troca de senha do próprio usuário (precisa estar autenticado)
                .requestMatchers("/api/admin/me/password").authenticated()

                // =========================
                // 🔓 AUTH TENANT
                // =========================
                .requestMatchers(
                    "/api/tenant/auth/login",
                    "/api/tenant/auth/refresh"
                ).permitAll()

                // =========================
                // 🔓 PASSWORD RESET TENANT
                // =========================
                .requestMatchers(
                    "/api/tenant/password/forgot",
                    "/api/tenant/password/reset"
                ).permitAll()

                // =========================
                // 🔓 SIGNUP / CHECKUSER
                // =========================
                .requestMatchers(
                    "/api/accounts/auth/checkuser",
                    "/api/signup"
                ).permitAll()

                // =========================
                // ✅ BOUNDARIES OFICIAIS
                // =========================
                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/api/controlplane/**").authenticated()
                .requestMatchers("/api/tenant/**").authenticated()

                // =========================
                // ❌ Qualquer rota fora disso é erro de arquitetura
                // =========================
                .anyRequest().denyAll()
            );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // ✅ MustChangePassword precisa rodar DEPOIS do JWT filter
        http.addFilterAfter(mustChangePasswordFilter(), JwtAuthenticationFilter.class);

        return http.build();
    }
}
