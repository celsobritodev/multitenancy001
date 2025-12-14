package brito.com.multitenancy001.controllers;


import brito.com.multitenancy001.configuration.ValidationPatterns;
import brito.com.multitenancy001.dtos.AccountCreateRequest;
import brito.com.multitenancy001.dtos.AccountResponse;
import brito.com.multitenancy001.dtos.CheckUserRequest;
import brito.com.multitenancy001.dtos.UserCreateRequest;
import brito.com.multitenancy001.dtos.UserResponse;
import brito.com.multitenancy001.services.AccountService;
import brito.com.multitenancy001.services.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {
    
    private final AccountService accountService;
    private final UserService userService;
    
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountCreateRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/{accountId}/users")
    public ResponseEntity<UserResponse> createUser(
            @PathVariable Long accountId,
            @Valid @RequestBody UserCreateRequest request) {
        
        UserResponse response = userService.createUser(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{accountId}/users")
    public ResponseEntity<List<UserResponse>> listUsersByAccount(@PathVariable Long accountId) {

        List<UserResponse> users = userService.listUsersByAccount(accountId);
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/{accountId}/users/active")
    public ResponseEntity<List<UserResponse>> listActiveUsersByAccount(
            @PathVariable Long accountId) {

        List<UserResponse> users = userService.listActiveUsersByAccount(accountId);
        return ResponseEntity.ok(users);
    }
    
    @PatchMapping("/{accountId}/users/{userId}/status")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable Long accountId,
            @PathVariable Long userId,
            @RequestParam boolean active) {

        UserResponse response = userService.updateUserStatus(accountId, userId, active);
        return ResponseEntity.ok(response);
    }
    
    
    @DeleteMapping("/{accountId}/users/{userId}")
    public ResponseEntity<String> softDeleteUser(
            @PathVariable Long accountId,
            @PathVariable Long userId) {

        userService.softDeleteUser(accountId, userId);
        return ResponseEntity.ok("Usuário removido com sucesso (soft delete).");
    }
    
    @PatchMapping("/{accountId}/users/{userId}/restore")
    public ResponseEntity<UserResponse> restoreUser(
            @PathVariable Long accountId,
            @PathVariable Long userId) {

        UserResponse response = userService.restoreUser(accountId, userId);
        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/{accountId}/users/{userId}/hard")
    public ResponseEntity<Void> hardDeleteUser(
            @PathVariable Long accountId,
            @PathVariable Long userId
    ) {
        userService.hardDeleteUser(accountId, userId);
        return ResponseEntity.noContent().build(); // 204
    }
    
    
    @PatchMapping("/{accountId}/users/{userId}/reset-password")
    public ResponseEntity<UserResponse> resetPassword(
            @PathVariable Long accountId,
            @PathVariable Long userId,
            @RequestParam
            @Pattern(regexp = ValidationPatterns.PASSWORD_PATTERN,
                 message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números")
           String newPassword) {

        UserResponse response = userService.resetPassword(accountId, userId, newPassword);
        return ResponseEntity.ok(response);
    }

    

    
    
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
    	String token = userService.generatePasswordResetToken(email);
    	return ResponseEntity.ok("Token gerado: " + token);
    }
 
    
    @PostMapping("/auth/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String token,
                                                @RequestParam String newPassword) {

        userService.resetPasswordWithToken(token, newPassword);
        return ResponseEntity.ok("Senha redefinida com sucesso.");
    }
    
    
 // No AccountController.java
 // 2. Modifique o controller
    @PostMapping("/auth/checkuser")
    public ResponseEntity<String> checkUserCredentials(
            @Valid @RequestBody CheckUserRequest request) {

        if (request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest()
                .body("Username e password são obrigatórios.");
        }

        boolean valid = userService.checkCredentials(
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