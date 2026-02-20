package brito.com.multitenancy001.tenant.suppliers.api;

import brito.com.multitenancy001.tenant.suppliers.api.dto.SupplierCreateRequest;
import brito.com.multitenancy001.tenant.suppliers.api.dto.SupplierResponse;
import brito.com.multitenancy001.tenant.suppliers.api.dto.SupplierUpdateRequest;
import brito.com.multitenancy001.tenant.suppliers.api.mapper.SupplierApiMapper;
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

/**
 * Controller HTTP para Suppliers no contexto do Tenant.
 *
 * Regras:
 * - Só HTTP: DTO + Mapper + Service
 * - Nunca receber Entity no @RequestBody
 * - Nunca retornar Entity
 */
@RestController
@RequestMapping("/api/tenant/suppliers")
@RequiredArgsConstructor
public class TenantSupplierController {

    private final TenantSupplierService tenantSupplierService;
    private final SupplierApiMapper supplierApiMapper;

    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.asAuthority())")
    public ResponseEntity<List<SupplierResponse>> listAll() {
        // Comentário do método: lista padrão (não-deletados) e mapeia para DTO.
        List<Supplier> list = tenantSupplierService.findAll();
        return ResponseEntity.ok(supplierApiMapper.toResponseList(list));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.asAuthority())")
    public ResponseEntity<List<SupplierResponse>> listActive() {
        // Comentário do método: lista apenas ativos (não-deletados) e mapeia para DTO.
        List<Supplier> list = tenantSupplierService.findActive();
        return ResponseEntity.ok(supplierApiMapper.toResponseList(list));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.asAuthority())")
    public ResponseEntity<SupplierResponse> getById(@PathVariable UUID id) {
        // Comentário do método: busca por id e retorna DTO.
        Supplier s = tenantSupplierService.findById(id);
        return ResponseEntity.ok(supplierApiMapper.toResponse(s));
    }

    @GetMapping("/document/{document}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.asAuthority())")
    public ResponseEntity<SupplierResponse> getByDocument(@PathVariable String document) {
        // Comentário do método: busca por document e retorna DTO.
        Supplier s = tenantSupplierService.findByDocument(document);
        return ResponseEntity.ok(supplierApiMapper.toResponse(s));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.asAuthority())")
    public ResponseEntity<List<SupplierResponse>> searchByName(@RequestParam("name") String name) {
        // Comentário do método: busca por nome e retorna lista DTO.
        List<Supplier> list = tenantSupplierService.searchByName(name);
        return ResponseEntity.ok(supplierApiMapper.toResponseList(list));
    }

    @GetMapping("/email")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.asAuthority())")
    public ResponseEntity<List<SupplierResponse>> getByEmail(@RequestParam("email") String email) {
        // Comentário do método: busca por email (não-deletados) e retorna DTO.
        List<Supplier> list = tenantSupplierService.findByEmail(email);
        return ResponseEntity.ok(supplierApiMapper.toResponseList(list));
    }

    @GetMapping("/email/any")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_READ.asAuthority())")
    public ResponseEntity<List<SupplierResponse>> findAnyByEmail(@RequestParam String email) {
        // Comentário do método: busca por email incluindo deletados (uso administrativo/diagnóstico).
        List<Supplier> list = tenantSupplierService.findAnyByEmail(email);
        return ResponseEntity.ok(supplierApiMapper.toResponseList(list));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.asAuthority())")
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierCreateRequest req) {
        // Comentário do método: converte DTO -> Command e delega regras ao Service.
        Supplier saved = tenantSupplierService.create(supplierApiMapper.toCreateCommand(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierApiMapper.toResponse(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.asAuthority())")
    public ResponseEntity<SupplierResponse> update(@PathVariable UUID id, @Valid @RequestBody SupplierUpdateRequest req) {
        // Comentário do método: converte DTO -> Command e aplica update com semântica "null não altera".
        Supplier updated = tenantSupplierService.update(id, supplierApiMapper.toUpdateCommand(req));
        return ResponseEntity.ok(supplierApiMapper.toResponse(updated));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.asAuthority())")
    public ResponseEntity<SupplierResponse> toggleActive(@PathVariable UUID id) {
        // Comentário do método: alterna active e retorna DTO.
        Supplier updated = tenantSupplierService.toggleActive(id);
        return ResponseEntity.ok(supplierApiMapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.asAuthority())")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id) {
        // Comentário do método: soft delete e retorna 204.
        tenantSupplierService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_SUPPLIER_WRITE.asAuthority())")
    public ResponseEntity<SupplierResponse> restore(@PathVariable UUID id) {
        // Comentário do método: restaura e retorna DTO.
        Supplier restored = tenantSupplierService.restore(id);
        return ResponseEntity.ok(supplierApiMapper.toResponse(restored));
    }
}