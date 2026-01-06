package brito.com.multitenancy001.platform.api.dto.users;



import java.time.LocalDateTime;
import java.util.List;

public record PlatformUserDetailsResponse(
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
    public PlatformUserDetailsResponse {
        if (permissions == null) {
            permissions = List.of();
        }
    }
}