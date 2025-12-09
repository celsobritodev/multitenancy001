package brito.com.example.multitenancy001.dtos;

public record AdminUserResponse(
	    Long id,
	    String username,
	    String email,
	    String role
	) {}