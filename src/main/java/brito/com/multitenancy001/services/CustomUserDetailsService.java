package brito.com.multitenancy001.services;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.entities.master.User;
import brito.com.multitenancy001.entities.master.UserRole;
import brito.com.multitenancy001.repositories.UserRepository;
import brito.com.multitenancy001.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        String tenant = TenantContext.getCurrentTenant();

        /*
         * ============================================
         * 1️⃣ SUPER ADMIN (schema public)
         * ============================================
         */
        if (tenant == null || "public".equals(tenant)) {

            User user = userRepository
                .findByUsernameAndDeletedFalse(username)
                .orElseThrow(() ->
                    new UsernameNotFoundException("Usuário não encontrado: " + username)
                );

            if (user.getRole() != UserRole.SUPER_ADMIN) {
                throw new UsernameNotFoundException(
                    "Usuário não é SUPER_ADMIN"
                );
            }

            return new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(
                    new SimpleGrantedAuthority(user.getRole().asAuthority())
                ),
                user.isAccountNonLocked(),
                null,            // ❌ SUPER_ADMIN não pertence a tenant
                null             // ❌ SUPER_ADMIN não tem accountId
            );
        }

        /*
         * ============================================
         * 2️⃣ USUÁRIO DE TENANT
         * ============================================
         */
        User user = userRepository
            .findByUsernameAndDeletedFalse(username)
            .orElseThrow(() ->
                new UsernameNotFoundException("Usuário não encontrado: " + username)
            );

        // 🚨 Segurança extra
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new UsernameNotFoundException(
                "SUPER_ADMIN não pode autenticar dentro de tenant"
            );
        }

        return new CustomUserDetails(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPassword(),
            Collections.singletonList(
                new SimpleGrantedAuthority(user.getRole().asAuthority())
            ),
            user.isAccountNonLocked(),
            user.getAccount().getSchemaName(),
            user.getAccount().getId()
        );
    }
}
