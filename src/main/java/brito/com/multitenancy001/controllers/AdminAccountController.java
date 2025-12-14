package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.AccountResponse;
import brito.com.multitenancy001.services.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountService accountService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AccountResponse>> listAllAccounts() {

        // 🔐 GARANTE schema public
        TenantContext.clear();

        return ResponseEntity.ok(accountService.listAllAccounts());
    }
}
