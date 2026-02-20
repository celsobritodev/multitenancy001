package brito.com.multitenancy001.tenant.categories.api;

import brito.com.multitenancy001.tenant.categories.api.dto.CategoryCreateRequest;
import brito.com.multitenancy001.tenant.categories.api.dto.CategoryResponse;
import brito.com.multitenancy001.tenant.categories.api.dto.CategoryUpdateRequest;
import brito.com.multitenancy001.tenant.categories.api.mapper.CategoryApiMapper;
import brito.com.multitenancy001.tenant.categories.app.TenantCategoryService;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de Categories do Tenant.
 *
 * Padrão definitivo (DDD / layered simples):
 * - Controller: somente HTTP (DTO + mapper)
 * - Service: Commands + regras de negócio
 * - Domain: entidades + invariantes simples
 */
@RestController
@RequestMapping("/api/tenant/categories")
@RequiredArgsConstructor
public class TenantCategoryController {

    private final TenantCategoryService tenantCategoryService;
    private final CategoryApiMapper categoryApiMapper;

    /**
     * Lista categorias (NOT deleted).
     */
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<CategoryResponse>> listAll() {
        // Comentário do método: contrato retorna DTO, não Entity.
        return ResponseEntity.ok(categoryApiMapper.toResponseList(tenantCategoryService.findAll()));
    }

    /**
     * Lista categorias ativas (NOT deleted, active=true).
     */
    @GetMapping("/active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<CategoryResponse>> listActive() {
        // Comentário do método: retorna apenas ativas (padrão atual do service).
        return ResponseEntity.ok(categoryApiMapper.toResponseList(tenantCategoryService.findActive()));
    }

    /**
     * Busca categoria por id (deleted => 404).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<CategoryResponse> getById(@PathVariable Long id) {
        // Comentário do método: service garante 404 quando deleted.
        Category c = tenantCategoryService.findById(id);
        return ResponseEntity.ok(categoryApiMapper.toResponse(c));
    }

    /**
     * Pesquisa categorias por nome (NOT deleted).
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<CategoryResponse>> search(@RequestParam("name") String name) {
        // Comentário do método: service valida required/blank.
        return ResponseEntity.ok(categoryApiMapper.toResponseList(tenantCategoryService.searchByName(name)));
    }

    /**
     * Lista categorias com flags administrativas (opcionalmente incluindo deletadas/inativas).
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<CategoryResponse>> listAdmin(
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        // Comentário do método: expõe deleted/active no DTO para suportar visão admin.
        return ResponseEntity.ok(
                categoryApiMapper.toResponseList(tenantCategoryService.findWithFlags(includeDeleted, includeInactive))
        );
    }

    /**
     * Cria categoria no tenant.
     */
    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest req) {
        // Comentário do método: DTO -> Command -> Service.
        Category saved = tenantCategoryService.create(categoryApiMapper.toCreateCommand(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryApiMapper.toResponse(saved));
    }

    /**
     * Atualiza categoria do tenant (PUT semântico).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest req) {
        // Comentário do método: service decide 404/409 conforme regras (deleted etc).
        Category updated = tenantCategoryService.update(id, categoryApiMapper.toUpdateCommand(req));
        return ResponseEntity.ok(categoryApiMapper.toResponse(updated));
    }

    /**
     * Alterna status ativo/inativo da categoria.
     */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<CategoryResponse> toggleActive(@PathVariable Long id) {
        // Comentário do método: mantém endpoint existente, apenas troca retorno para DTO.
        return ResponseEntity.ok(categoryApiMapper.toResponse(tenantCategoryService.toggleActive(id)));
    }

    /**
     * Soft-delete idempotente: sempre 204 (mesmo se não existir / já estiver deletada).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        // Comentário do método: service implementa idempotência.
        tenantCategoryService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restaura categoria previamente deletada (200 com body).
     */
    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<CategoryResponse> restore(@PathVariable Long id) {
        // Comentário do método: 404 se não existir; 200 com DTO se restaurar.
        return ResponseEntity.ok(categoryApiMapper.toResponse(tenantCategoryService.restore(id)));
    }
}