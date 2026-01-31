package brito.com.multitenancy001.tenant.categories.api;

import brito.com.multitenancy001.tenant.categories.app.TenantSubcategoryService;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
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

    // Lista subcategorias (não-deletadas) do tenant.
    @GetMapping
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Subcategory>> listAll() {
        return ResponseEntity.ok(tenantSubcategoryService.findAll());
    }

    // Lista subcategorias ativas (não-deletadas) do tenant.
    @GetMapping("/active")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Subcategory>> listActive() {
        return ResponseEntity.ok(tenantSubcategoryService.findActive());
    }

    // Busca subcategoria por id (escopo: tenant).
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<Subcategory> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantSubcategoryService.findById(id));
    }

    // Lista subcategorias por categoria (default: apenas ativas e não-deletadas).
    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Subcategory>> listByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(tenantSubcategoryService.findByCategoryId(categoryId));
    }

    // Lista subcategorias por categoria com flags administrativas (incluir deletadas/inativas).
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

    // Cria subcategoria dentro de uma categoria.
    @PostMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Subcategory> create(
            @PathVariable Long categoryId,
            @Valid @RequestBody Subcategory subcategory
    ) {
        Subcategory saved = tenantSubcategoryService.create(categoryId, subcategory);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // Atualiza subcategoria do tenant (substituição completa).
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Subcategory> update(@PathVariable Long id, @RequestBody Subcategory subcategory) {
        return ResponseEntity.ok(tenantSubcategoryService.update(id, subcategory));
    }

    // Alterna status ativo/inativo da subcategoria.
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Subcategory> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(tenantSubcategoryService.toggleActive(id));
    }

    // Soft-delete de subcategoria no tenant.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        tenantSubcategoryService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // Restaura subcategoria previamente deletada (soft-delete).
    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_WRITE')")
    public ResponseEntity<Subcategory> restore(@PathVariable Long id) {
        return ResponseEntity.ok(tenantSubcategoryService.restore(id));
    }
    
    // Lista subcategorias por categoria (NOT DELETED, inclui inativas).
    @GetMapping("/category/{categoryId}/not-deleted")
    @PreAuthorize("hasAuthority('TEN_CATEGORY_READ')")
    public ResponseEntity<List<Subcategory>> listByCategoryNotDeleted(@PathVariable Long categoryId) {
        return ResponseEntity.ok(tenantSubcategoryService.findByCategoryIdNotDeleted(categoryId));
    }

}
