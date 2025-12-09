package brito.com.example.multitenancy001.dtos;

import jakarta.validation.constraints.NotBlank;

//DTO usando Records (Java 14+)
public record LoginRequest(
 @NotBlank String username,
 @NotBlank String password
) {}

