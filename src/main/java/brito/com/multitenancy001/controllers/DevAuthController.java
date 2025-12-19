package brito.com.multitenancy001.controllers;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.dtos.CheckUserRequest;
import brito.com.multitenancy001.services.UserTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dev/auth")
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class DevAuthController {
	
	   private final UserTenantService tenantUserService;
	
	 @PostMapping("/check-credentials")
	    public ResponseEntity<String> checkUserCredentials(
	            @Valid @RequestBody CheckUserRequest request) {

	       

	        boolean valid = tenantUserService.checkCredentials(
	                request.slug(),
	                request.username(),
	                request.password()
	        );

	        if (!valid) {
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                    .body("Usuário ou senha incorretos.");
	        }

	        return ResponseEntity.ok("Credenciais válidas.");
	    }

}
