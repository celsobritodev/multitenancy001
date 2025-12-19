package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.configuration.TenantContext;
import brito.com.multitenancy001.dtos.AccountAdminDetailsResponse;
import brito.com.multitenancy001.dtos.AccountResponse;
import brito.com.multitenancy001.dtos.StatusRequest;
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

    
    // listar contas
    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAllAccounts() {
        try {
            TenantContext.clear();
            return ResponseEntity.ok(accountService.listAllAccounts());
        } finally {
            TenantContext.clear(); // 🔥 evita vazamento de tenant
        }
    }
    
    // Ver conta

    // ✅ NOVO ENDPOINT
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getById(@PathVariable Long id) {
        try {
            TenantContext.clear(); // 🔥 garante PUBLIC
            return ResponseEntity.ok(accountService.getAccountDetails(id));
        } finally {
            TenantContext.clear();
        }
    }
    
    
    // detalhes de uma conta
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
 
    
    //Ativar / suspender / cancelar conta
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable Long id,
            @RequestBody StatusRequest req
    ) {
        try {
            TenantContext.clear(); // 🔥 PUBLIC SEMPRE
            accountService.changeAccountStatus(id, req);
            return ResponseEntity.noContent().build();
        } finally {
            TenantContext.clear();
        }
    }

    
    
   
    // Alterar plano e limites
    //@PatchMapping("/{id}/plan")
    //public void changePlan(@PathVariable Long id, @RequestBody PlanRequest req) { }

    // Soft Delete
    //@DeleteMapping("/{id}")
    //public void softDelete(@PathVariable Long id) {  }

    // restore
   // @PostMapping("/{id}/restore")
   // public void restore(@PathVariable Long id) {  }
    
    // Reset administrativo de conta (suporte)
   // @PostMapping("/{id}/reset")
    //public void reset(@PathVariable Long id) {  }
   
    // Acesso de suporte (impersonation) 🚨 avançado
   // @PostMapping("/{id}/impersonate")
    //public void impersonate(@PathVariable Long id) {  }
}
