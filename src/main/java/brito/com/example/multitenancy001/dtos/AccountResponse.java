package brito.com.example.multitenancy001.dtos;




import java.time.LocalDateTime;

public record AccountResponse(
    Long id,
    String name,
    String schemaName,
    String status,
    LocalDateTime createdAt,  // ✅ Mantém LocalDate no DTO
    LocalDateTime trialEndDate,
    AdminUserResponse adminUser
) {}