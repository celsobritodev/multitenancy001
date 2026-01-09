
package brito.com.multitenancy001.controlplane.api.controller.accounts;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.application.AccountLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
public class AccountSignupController {
    
    private final AccountLifecycleService accountLifecycleService;

    @PostMapping
    public ResponseEntity<AccountResponse> signup(
            @Valid @RequestBody SignupRequest request) {
        AccountResponse response = accountLifecycleService.createAccount( request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}