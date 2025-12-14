package brito.com.multitenancy001.services;



import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.entities.master.User;
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

        if (tenant == null) {
            throw new UsernameNotFoundException("Tenant não definido");
        }

        User user = userRepository
            .findByUsernameAndDeletedFalse(username)
            .orElseThrow(() ->
                new UsernameNotFoundException("Usuário não encontrado: " + username)
            );

        return new CustomUserDetails(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPassword(),
            Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            ),
            user.isAccountNonLocked(),
            user.getAccount().getSchemaName(),
            user.getAccount().getId()
        );
    }
}

