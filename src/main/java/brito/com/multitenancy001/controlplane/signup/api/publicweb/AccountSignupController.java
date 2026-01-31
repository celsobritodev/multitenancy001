package brito.com.multitenancy001.controlplane.signup.api.publicweb;

import brito.com.multitenancy001.controlplane.accounts.app.AccountLifecycleService;
import brito.com.multitenancy001.controlplane.signup.api.dto.SignupRequest;
import brito.com.multitenancy001.controlplane.signup.api.dto.SignupResponse;
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
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest signupRequest) {
        SignupResponse response = accountLifecycleService.createAccount(signupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
