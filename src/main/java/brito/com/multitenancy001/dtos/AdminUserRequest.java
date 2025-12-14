package brito.com.multitenancy001.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminUserRequest(

        @NotBlank
        String username,

        @Email
        String email,

        @NotBlank
        String password,

        @NotBlank
        String confirmPassword
) {}
