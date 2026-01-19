package brito.com.multitenancy001.tenant.api.controller.categories;

import brito.com.multitenancy001.tenant.application.category.TenantCategoryService;
import brito.com.multitenancy001.tenant.domain.category.Category;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/categories")
@RequiredArgsConstructor
public class TenantCategoryController {

    private final TenantCategoryService tenantCategoryService;

    // ======================
    // READ
    // ======================

    @GetMapping
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Category>> listAll() {
        return ResponseEntity.ok(tenantCategoryService.findAll());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Category>> listActive() {
        return ResponseEntity.ok(tenantCategoryService.findActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<Category> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantCategoryService.findById(id));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Category>> search(@RequestParam("name") String name) {
        return ResponseEntity.ok(tenantCategoryService.searchByName(name));
    }

    // Admin flags
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Category>> listAdmin(
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(tenantCategoryService.findWithFlags(includeDeleted, includeInactive));
    }

    // ======================
    // WRITE
    // ======================

    @PostMapping
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Category> create(@Valid @RequestBody Category category) {
        Category saved = tenantCategoryService.create(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Category> update(@PathVariable Long id, @RequestBody Category category) {
        return ResponseEntity.ok(tenantCategoryService.update(id, category));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Category> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(tenantCategoryService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        tenantCategoryService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Category> restore(@PathVariable Long id) {
        return ResponseEntity.ok(tenantCategoryService.restore(id));
    }
}
