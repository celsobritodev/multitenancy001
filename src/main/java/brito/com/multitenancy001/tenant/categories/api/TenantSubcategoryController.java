package brito.com.multitenancy001.tenant.categories.api;

import brito.com.multitenancy001.tenant.categories.api.dto.SubcategoryCreateRequest;
import brito.com.multitenancy001.tenant.categories.api.dto.SubcategoryResponse;
import brito.com.multitenancy001.tenant.categories.api.dto.SubcategoryUpdateRequest;
import brito.com.multitenancy001.tenant.categories.api.mapper.SubcategoryApiMapper;
import brito.com.multitenancy001.tenant.categories.app.TenantSubcategoryService;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de Subcategories do Tenant.
 *
 * Padrão definitivo:
 * - Controller só HTTP (DTO/mapper)
 * - Service com Commands/regras
 */
@RestController
@RequestMapping("/api/tenant/subcategories")
@RequiredArgsConstructor
public class TenantSubcategoryController {

    private final TenantSubcategoryService tenantSubcategoryService;
    private final SubcategoryApiMapper subcategoryApiMapper;

    /**
     * Busca subcategoria por id (deleted => 404).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<SubcategoryResponse> getById(@PathVariable Long id) {
        // Comentário do método: service garante 404 quando deleted.
        Subcategory s = tenantSubcategoryService.findById(id);
        return ResponseEntity.ok(subcategoryApiMapper.toResponse(s));
    }

    /**
     * Lista subcategorias por categoria (admin flags).
     */
    @GetMapping("/category/{categoryId}/admin")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<SubcategoryResponse>> listByCategoryAdmin(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        // Comentário do método: retorna DTO, não entity.
        return ResponseEntity.ok(
                subcategoryApiMapper.toResponseList(
                        tenantSubcategoryService.findByCategoryIdAdmin(categoryId, includeDeleted, includeInactive)
                )
        );
    }

    /**
     * Lista subcategorias por categoria (NOT deleted, inclui inativas).
     */
    @GetMapping("/category/{categoryId}/not-deleted")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_READ.asAuthority())")
    public ResponseEntity<List<SubcategoryResponse>> listByCategoryNotDeleted(@PathVariable Long categoryId) {
        // Comentário do método: mantém endpoint existente, só muda retorno.
        return ResponseEntity.ok(
                subcategoryApiMapper.toResponseList(tenantSubcategoryService.findByCategoryIdNotDeleted(categoryId))
        );
    }

    /**
     * Cria subcategoria dentro de uma categoria.
     */
    @PostMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<SubcategoryResponse> create(
            @PathVariable Long categoryId,
            @Valid @RequestBody SubcategoryCreateRequest req
    ) {
        // Comentário do método: DTO + path -> Command -> Service.
        Subcategory saved = tenantSubcategoryService.create(subcategoryApiMapper.toCreateCommand(categoryId, req));
        return ResponseEntity.status(HttpStatus.CREATED).body(subcategoryApiMapper.toResponse(saved));
    }

    /**
     * Atualiza subcategoria (PUT semântico).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<SubcategoryResponse> update(@PathVariable Long id, @Valid @RequestBody SubcategoryUpdateRequest req) {
        // Comentário do método: service aplica regras (409 se deletada etc).
        Subcategory updated = tenantSubcategoryService.update(id, subcategoryApiMapper.toUpdateCommand(req));
        return ResponseEntity.ok(subcategoryApiMapper.toResponse(updated));
    }

    /**
     * Alterna status ativo/inativo.
     */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<SubcategoryResponse> toggleActive(@PathVariable Long id) {
        // Comentário do método: mantém comportamento, retorna DTO.
        return ResponseEntity.ok(subcategoryApiMapper.toResponse(tenantSubcategoryService.toggleActive(id)));
    }

    /**
     * Soft-delete idempotente: sempre 204.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        // Comentário do método: idempotência é no service.
        tenantSubcategoryService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restore: 200 com body.
     */
    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_CATEGORY_WRITE.asAuthority())")
    public ResponseEntity<SubcategoryResponse> restore(@PathVariable Long id) {
        // Comentário do método: 404 se não existe; 200 com DTO.
        return ResponseEntity.ok(subcategoryApiMapper.toResponse(tenantSubcategoryService.restore(id)));
    }
}