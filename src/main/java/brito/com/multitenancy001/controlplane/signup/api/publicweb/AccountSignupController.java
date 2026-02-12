package brito.com.multitenancy001.controlplane.signup.api.publicweb;

import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.accounts.app.AccountAppService;
import brito.com.multitenancy001.controlplane.signup.api.dto.SignupRequest;
import brito.com.multitenancy001.controlplane.signup.api.dto.SignupResponse;
import brito.com.multitenancy001.controlplane.signup.api.dto.TenantAdminResponse;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
public class AccountSignupController {

    private final AccountAppService accountLifecycleService;
    private final AccountApiMapper accountApiMapper;

    @PostMapping
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest req) {

        SignupResult result = accountLifecycleService.createAccount(new SignupCommand(
                req.displayName(),
                req.loginEmail(),
                req.taxIdType(),
                req.taxIdNumber(),
                req.password(),
                req.confirmPassword()
        ));

        SignupResponse http = new SignupResponse(
                accountApiMapper.toResponse(result.account()),
                new TenantAdminResponse(
                        result.tenantAdmin().id(),
                        result.tenantAdmin().email(),
                        result.tenantAdmin().role()
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(http);
    }
}

