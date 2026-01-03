// src/main/java/brito/com/multitenancy001/controllers/SignupController.java
package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.dtos.*;
import brito.com.multitenancy001.services.AccountProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
public class SignupController {
    
    private final AccountProvisioningService accountProvisioningService;

    @PostMapping
    public ResponseEntity<AccountResponse> signup(
            @Valid @RequestBody SignupRequest request) {
        AccountResponse response = accountProvisioningService.createAccount( request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}