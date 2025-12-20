package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.*;
import brito.com.multitenancy001.entities.account.Account;
import brito.com.multitenancy001.entities.account.UserAccount;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.UserAccountRepository;
import brito.com.multitenancy001.services.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final UserAccountRepository userAccountRepository;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAllAccounts() {
        try {
            TenantContext.clear();
            return ResponseEntity.ok(accountService.listAllAccountsWithAdmin());
        } finally {
            TenantContext.clear();
        }
    }
    

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getById(@PathVariable Long id) {
        try {
            TenantContext.clear();
            Account account = accountRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(
                    "ACCOUNT_NOT_FOUND",
                    "Conta não encontrada",
                    404
                ));
            
            // Busca admin da conta
            UserAccount admin = userAccountRepository
                .findFirstByAccountIdAndDeletedFalse(account.getId())
                .orElse(null);
                
            AccountResponse response = AccountResponse.fromEntity(account, admin);
            return ResponseEntity.ok(response);
        } finally {
            TenantContext.clear();
        }
    }
    
    
    
    @GetMapping("/{id}/details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AccountAdminDetailsResponse> getDetails(@PathVariable Long id) {
        try {
            TenantContext.clear();
            return ResponseEntity.ok(accountService.getAccountAdminDetails(id));
        } finally {
            TenantContext.clear();
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable Long id,
            @RequestBody StatusRequest req
    ) {
        try {
            TenantContext.clear();
            accountService.changeAccountStatus(id, req);
            return ResponseEntity.noContent().build();
        } finally {
            TenantContext.clear();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        try {
            TenantContext.clear();
            accountService.softDeleteAccount(id);
            return ResponseEntity.noContent().build();
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> restore(@PathVariable Long id) {
        try {
            TenantContext.clear();
            accountService.restoreAccount(id);
            return ResponseEntity.noContent().build();
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/{id}/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> reset(@PathVariable Long id) {
        try {
            TenantContext.clear();
            
            // Valida se não é conta do sistema
            Account account = accountService.getAccountById(id);
            if (account.isSystemAccount()) {
                throw new ApiException(
                    "SYSTEM_ACCOUNT_PROTECTED",
                    "Não é permitido resetar contas do sistema",
                    403
                );
            }
            
            // accountService.resetAccount(id);
            return ResponseEntity.noContent().build();
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/{id}/impersonate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> impersonate(@PathVariable Long id) {
        try {
            TenantContext.clear();
            
            // Valida se não é conta do sistema
            Account account = accountService.getAccountById(id);
            if (account.isSystemAccount()) {
                throw new ApiException(
                    "SYSTEM_ACCOUNT_PROTECTED",
                    "Não é permitido acessar como conta do sistema",
                    403
                );
            }
            
            // accountService.impersonateAccount(id);
            return ResponseEntity.noContent().build();
        } finally {
            TenantContext.clear();
        }
    }

    @PatchMapping("/{id}/plan")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> changePlan(@PathVariable Long id, @RequestBody PlanRequest req) {
        try {
            TenantContext.clear();
            
            // Valida se não é conta do sistema
            Account account = accountService.getAccountById(id);
            if (account.isSystemAccount()) {
                throw new ApiException(
                    "SYSTEM_ACCOUNT_PROTECTED",
                    "Não é permitido alterar plano de contas do sistema",
                    403
                );
            }
            
            // accountService.changePlan(id, req);
            return ResponseEntity.noContent().build();
        } finally {
            TenantContext.clear();
        }
    }
}