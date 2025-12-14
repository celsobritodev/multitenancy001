package brito.com.multitenancy001.security;



import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;

public class CustomUserDetails implements UserDetails {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final Long id;
    private final String username;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;
    private final String tenantSchema;
    private final Long accountId;

    public CustomUserDetails(
            Long id,
            String username,
            String email,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            boolean active,
            String tenantSchema,
            Long accountId
    ) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
        this.active = active;
        this.tenantSchema = tenantSchema;
        this.accountId = accountId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities; // já recebido no construtor
    }

    @Override
    public String getPassword() {
        return password; // já recebido no construtor
    }

    @Override
    public String getUsername() {
        return username; // já recebido no construtor
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // pode adaptar
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // pode adaptar
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // pode adaptar
    }

    @Override
    public boolean isEnabled() {
        return active; // já recebido no construtor
    }

    // ---- getters extras usados no JWT ----

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getTenantSchema() {
        return tenantSchema;
    }
}
