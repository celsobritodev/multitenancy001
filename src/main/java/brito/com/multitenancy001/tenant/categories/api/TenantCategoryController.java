package brito.com.multitenancy001.tenant.categories.api;

import brito.com.multitenancy001.tenant.categories.app.TenantCategoryService;
import brito.com.multitenancy001.tenant.categories.domain.Category;
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

    // Lista categorias (não-deletadas) do tenant.
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<Category>> listAll() {
        return ResponseEntity.ok(tenantCategoryService.findAll());
    }

    // Lista categorias ativas (não-deletadas) do tenant.
    @GetMapping("/active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<Category>> listActive() {
        return ResponseEntity.ok(tenantCategoryService.findActive());
    }

    // Busca categoria por id (escopo: tenant).
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<Category> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantCategoryService.findById(id));
    }

    // Pesquisa categorias por nome (escopo: tenant).
    @GetMapping("/search")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<Category>> search(@RequestParam("name") String name) {
        return ResponseEntity.ok(tenantCategoryService.searchByName(name));
    }

    // Lista categorias com flags administrativas (incluir deletadas/inativas).
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<Category>> listAdmin(
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(tenantCategoryService.findWithFlags(includeDeleted, includeInactive));
    }

    // Cria categoria no tenant.
    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<Category> create(@Valid @RequestBody Category category) {
        Category saved = tenantCategoryService.create(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // Atualiza categoria do tenant (substituição completa).
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<Category> update(@PathVariable Long id, @RequestBody Category category) {
        return ResponseEntity.ok(tenantCategoryService.update(id, category));
    }

    // Alterna status ativo/inativo da categoria.
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<Category> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(tenantCategoryService.toggleActive(id));
    }

    // Soft-delete de categoria no tenant.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        tenantCategoryService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // Restaura categoria previamente deletada (soft-delete).
    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<Category> restore(@PathVariable Long id) {
        return ResponseEntity.ok(tenantCategoryService.restore(id));
    }
}
