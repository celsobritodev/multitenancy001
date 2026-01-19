package brito.com.multitenancy001.tenant.api.controller.categories;

import brito.com.multitenancy001.tenant.application.category.TenantSubcategoryService;
import brito.com.multitenancy001.tenant.domain.category.Subcategory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/subcategories")
@RequiredArgsConstructor
public class TenantSubcategoryController {

    private final TenantSubcategoryService tenantSubcategoryService;

    // ======================
    // READ
    // ======================

    @GetMapping
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Subcategory>> listAll() {
        return ResponseEntity.ok(tenantSubcategoryService.findAll());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Subcategory>> listActive() {
        return ResponseEntity.ok(tenantSubcategoryService.findActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<Subcategory> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantSubcategoryService.findById(id));
    }

    // subcategories por categoria (default: somente active + notDeleted)
    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Subcategory>> listByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(tenantSubcategoryService.findByCategoryId(categoryId));
    }

    // admin flags (deletados/inativos)
    @GetMapping("/category/{categoryId}/admin")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Subcategory>> listByCategoryAdmin(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(
                tenantSubcategoryService.findByCategoryIdAdmin(categoryId, includeDeleted, includeInactive)
        );
    }

    // ======================
    // WRITE
    // ======================

    // cria subcategoria dentro de uma categoria
    @PostMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Subcategory> create(
            @PathVariable Long categoryId,
            @Valid @RequestBody Subcategory subcategory
    ) {
        Subcategory saved = tenantSubcategoryService.create(categoryId, subcategory);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Subcategory> update(@PathVariable Long id, @RequestBody Subcategory subcategory) {
        return ResponseEntity.ok(tenantSubcategoryService.update(id, subcategory));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Subcategory> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(tenantSubcategoryService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        tenantSubcategoryService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Subcategory> restore(@PathVariable Long id) {
        return ResponseEntity.ok(tenantSubcategoryService.restore(id));
    }
}
