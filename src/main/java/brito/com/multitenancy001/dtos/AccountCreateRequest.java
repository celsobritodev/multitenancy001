package brito.com.multitenancy001.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountCreateRequest(
    @NotBlank @Size(min = 3, max = 100) 
    String name,
    
    @NotBlank @Email
    String companyEmail,
    
    @NotBlank
    String companyDocument,
    
    @Valid
    AdminCreateRequest  admin
) {}
