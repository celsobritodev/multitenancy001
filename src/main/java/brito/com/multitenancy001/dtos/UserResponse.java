package brito.com.multitenancy001.dtos;



import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
    Long id,
    String username,
    String name,
    String email,
    String role,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long accountId,
    List<String> permissions
) {
    public UserResponse {
        if (permissions == null) {
            permissions = List.of();
        }
    }
}