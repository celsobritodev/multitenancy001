package brito.com.example.multitenancy001.dtos;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
	    @NotBlank @Size(min = 3, max = 100) String name,
	    @NotBlank @Email String email,
	    @NotBlank @Size(min = 8) String password,
	    @NotBlank String role,
	    List<String> permissions
	) {
	    public UserCreateRequest {
	        if (permissions == null) {
	            permissions = new ArrayList<>();
	        }
	    }
	}