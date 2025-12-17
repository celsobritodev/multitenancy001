package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.dtos.AccountCreateRequest;
import brito.com.multitenancy001.dtos.AccountResponse;
import brito.com.multitenancy001.dtos.CheckUserRequest;
import brito.com.multitenancy001.services.AccountService;

import brito.com.multitenancy001.services.UserTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {
    
    private final AccountService accountService;
    private final UserTenantService tenantUserService;
    
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountCreateRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
   
    
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        String token = tenantUserService.generatePasswordResetToken(email);
        return ResponseEntity.ok("Token gerado: " + token);
    }
    
    @PostMapping("/auth/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String token,
                                                @RequestParam String newPassword) {
        tenantUserService.resetPasswordWithToken(token, newPassword);
        return ResponseEntity.ok("Senha redefinida com sucesso.");
    }
    
    @PostMapping("/auth/checkuser")
    public ResponseEntity<String> checkUserCredentials(
            @Valid @RequestBody CheckUserRequest request) {

        if (request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest()
                .body("Username e password são obrigatórios.");
        }

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