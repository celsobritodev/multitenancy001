package brito.com.example.multitenancy001.controllers;


import brito.com.example.multitenancy001.dtos.AccountCreateRequest;
import brito.com.example.multitenancy001.dtos.AccountResponse;
import brito.com.example.multitenancy001.dtos.UserCreateRequest;
import brito.com.example.multitenancy001.dtos.UserResponse;
import brito.com.example.multitenancy001.services.AccountService;
import brito.com.example.multitenancy001.services.UserService;
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
}