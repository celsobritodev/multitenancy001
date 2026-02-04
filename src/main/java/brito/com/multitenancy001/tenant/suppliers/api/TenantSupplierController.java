package brito.com.multitenancy001.tenant.suppliers.api;

import brito.com.multitenancy001.tenant.suppliers.app.TenantSupplierService;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant/suppliers")
@RequiredArgsConstructor
public class TenantSupplierController {

    private final TenantSupplierService tenantSupplierService;

    // Lista fornecedores (não-deletados) do tenant.
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.name())")
    public ResponseEntity<List<Supplier>> listAll() {
        return ResponseEntity.ok(tenantSupplierService.findAll());
    }

    // Lista fornecedores ativos (não-deletados) do tenant.
    @GetMapping("/active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.name())")
    public ResponseEntity<List<Supplier>> listActive() {
        return ResponseEntity.ok(tenantSupplierService.findActive());
    }

    // Busca fornecedor por id (escopo: tenant).
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.name())")
    public ResponseEntity<Supplier> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantSupplierService.findById(id));
    }

    // Busca fornecedor por documento (CPF/CNPJ) no tenant.
    @GetMapping("/document/{document}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.name())")
    public ResponseEntity<Supplier> getByDocument(@PathVariable String document) {
        return ResponseEntity.ok(tenantSupplierService.findByDocument(document));
    }

    // Pesquisa fornecedores por nome.
    @GetMapping("/search")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.name())")
    public ResponseEntity<List<Supplier>> searchByName(@RequestParam("name") String name) {
        return ResponseEntity.ok(tenantSupplierService.searchByName(name));
    }

    // Lista fornecedores por email.
    @GetMapping("/email")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.name())")
    public ResponseEntity<List<Supplier>> getByEmail(@RequestParam("email") String email) {
        return ResponseEntity.ok(tenantSupplierService.findByEmail(email));
    }

    // Cria fornecedor no tenant.
    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.name())")
    public ResponseEntity<Supplier> create(@Valid @RequestBody Supplier supplier) {
        Supplier saved = tenantSupplierService.create(supplier);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // Atualiza fornecedor do tenant (substituição completa).
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.name())")
    public ResponseEntity<Supplier> update(@PathVariable UUID id, @RequestBody Supplier supplier) {
        Supplier updated = tenantSupplierService.update(id, supplier);
        return ResponseEntity.ok(updated);
    }

    // Alterna status ativo/inativo do fornecedor.
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.name())")
    public ResponseEntity<Supplier> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantSupplierService.toggleActive(id));
    }

    // Soft-delete de fornecedor no tenant.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.name())")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id) {
        tenantSupplierService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // Restaura fornecedor previamente deletado (soft-delete).
    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.name())")
    public ResponseEntity<Supplier> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantSupplierService.restore(id));
    }

    // "Any" (pode incluir deleted/inactive) - útil para admin/auditoria
    @GetMapping("/email/any")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.name())")
    public ResponseEntity<List<Supplier>> findAnyByEmail(@RequestParam String email) {
        return ResponseEntity.ok(tenantSupplierService.findAnyByEmail(email));
    }
}

