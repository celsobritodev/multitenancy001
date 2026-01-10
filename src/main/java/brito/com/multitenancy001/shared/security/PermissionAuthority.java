package brito.com.multitenancy001.shared.security;

public interface PermissionAuthority {
    String asAuthority(); // ex: "CP_USER_READ" ou "TEN_USER_READ"
}
