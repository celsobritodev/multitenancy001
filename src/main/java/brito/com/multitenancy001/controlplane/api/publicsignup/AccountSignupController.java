package brito.com.multitenancy001.controlplane.api.publicsignup;

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
    public ResponseEntity<AccountResponse> signup(@Valid @RequestBody SignupRequest signupRequest) {
        AccountResponse response = accountLifecycleService.createAccount(signupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
