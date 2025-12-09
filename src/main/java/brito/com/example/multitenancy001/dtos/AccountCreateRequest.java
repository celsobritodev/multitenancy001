package brito.com.example.multitenancy001.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountCreateRequest(
    @NotBlank @Size(min = 3, max = 100) String name,
    @NotBlank @Email String adminEmail,
    @NotBlank @Size(min = 8) String adminPassword
) {}
