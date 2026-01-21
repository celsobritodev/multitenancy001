package brito.com.multitenancy001.shared.security;

import java.io.Serializable;

public interface RoleAuthority extends Serializable {
    String asAuthority();
}
