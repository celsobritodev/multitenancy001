package brito.com.multitenancy001.controlplane.security;

import brito.com.multitenancy001.shared.security.SystemRoleName;

public final class ControlPlaneSystemRoleMapper {

    private ControlPlaneSystemRoleMapper() {}

    public static SystemRoleName toSystemRole(ControlPlaneRole role) {
        if (role == null) return null;
        // nomes são iguais
        return SystemRoleName.valueOf(role.name());
    }
}
