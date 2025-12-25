package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.dtos.AccountCreateRequest;
import brito.com.multitenancy001.dtos.AccountResponse;
import brito.com.multitenancy001.services.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
public class SignupController {
    
    private final AccountService accountService;

    
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountCreateRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
   
}