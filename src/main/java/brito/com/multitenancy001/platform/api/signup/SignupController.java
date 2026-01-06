// src/main/java/brito/com/multitenancy001/controllers/SignupController.java
package brito.com.multitenancy001.platform.api.signup;

import brito.com.multitenancy001.platform.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.platform.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.platform.application.AccountLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
public class SignupController {
    
    private final AccountLifecycleService accountProvisioningService;

    @PostMapping
    public ResponseEntity<AccountResponse> signup(
            @Valid @RequestBody SignupRequest request) {
        AccountResponse response = accountProvisioningService.createAccount( request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}