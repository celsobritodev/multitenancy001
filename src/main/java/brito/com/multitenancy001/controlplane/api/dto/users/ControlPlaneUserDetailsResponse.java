package brito.com.multitenancy001.controlplane.api.dto.users;



import java.time.LocalDateTime;
import java.util.List;

public record ControlPlaneUserDetailsResponse(
    Long id,
    String username,
    String name,
    String email,
    String role,
    boolean suspendedByAccount,
    boolean suspendedByAdmin,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long accountId,
    List<String> permissions
) {
    public ControlPlaneUserDetailsResponse {
        if (permissions == null) {
            permissions = List.of();
        }
    }
}