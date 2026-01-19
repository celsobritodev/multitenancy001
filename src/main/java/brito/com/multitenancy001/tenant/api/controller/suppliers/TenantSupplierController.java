package brito.com.multitenancy001.tenant.api.controller.suppliers;

import brito.com.multitenancy001.tenant.application.supplier.TenantSupplierService;
import brito.com.multitenancy001.tenant.domain.supplier.Supplier;
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

    // =========================================================
    // READ (não-deletados)
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_READ')")
    public ResponseEntity<List<Supplier>> listAll() {
        return ResponseEntity.ok(tenantSupplierService.findAll());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_READ')")
    public ResponseEntity<List<Supplier>> listActive() {
        return ResponseEntity.ok(tenantSupplierService.findActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_READ')")
    public ResponseEntity<Supplier> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantSupplierService.findById(id));
    }

    @GetMapping("/document/{document}")
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_READ')")
    public ResponseEntity<Supplier> getByDocument(@PathVariable String document) {
        return ResponseEntity.ok(tenantSupplierService.findByDocument(document));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_READ')")
    public ResponseEntity<List<Supplier>> searchByName(@RequestParam("name") String name) {
        return ResponseEntity.ok(tenantSupplierService.searchByName(name));
    }

    @GetMapping("/email")
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_READ')")
    public ResponseEntity<List<Supplier>> getByEmail(@RequestParam("email") String email) {
        return ResponseEntity.ok(tenantSupplierService.findByEmail(email));
    }

    // =========================================================
    // WRITE
    // =========================================================

    @PostMapping
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_WRITE')")
    public ResponseEntity<Supplier> create(@Valid @RequestBody Supplier supplier) {
        Supplier saved = tenantSupplierService.create(supplier);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_WRITE')")
    public ResponseEntity<Supplier> update(@PathVariable UUID id, @RequestBody Supplier supplier) {
        Supplier updated = tenantSupplierService.update(id, supplier);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_WRITE')")
    public ResponseEntity<Supplier> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantSupplierService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_WRITE')")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id) {
        tenantSupplierService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('TEN_SUPPLIER_WRITE')")
    public ResponseEntity<Supplier> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantSupplierService.restore(id));
    }
}
