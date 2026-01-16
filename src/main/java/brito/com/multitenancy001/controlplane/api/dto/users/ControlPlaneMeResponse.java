package brito.com.multitenancy001.controlplane.api.dto.users;

import java.util.List;

public record ControlPlaneMeResponse(
        Long userId,
        String username,
        String email,
        String roleAuthority,
        Long accountId,
        boolean mustChangePassword,
        List<String> authorities
) {}
