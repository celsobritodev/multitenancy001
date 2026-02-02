package brito.com.multitenancy001.controlplane.auth.app.command;



public record ControlPlaneAdminLoginCommand(
        String email,
        String password
) { }
