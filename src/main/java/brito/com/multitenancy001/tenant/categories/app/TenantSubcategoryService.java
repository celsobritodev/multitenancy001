package brito.com.multitenancy001.tenant.categories.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.categories.persistence.TenantCategoryRepository;
import brito.com.multitenancy001.tenant.categories.persistence.TenantSubcategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubcategoryService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantSubcategoryRepository tenantSubcategoryRepository;
    private final TenantCategoryRepository tenantCategoryRepository;

    // =========================================================
    // READ
    // =========================================================

    @TenantReadOnlyTx
    public Subcategory findById(Long id) {
        if (id == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "id é obrigatório");

        Subcategory s = tenantSubcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND));

        if (s.isDeleted()) {
            throw new ApiException(ApiErrorCode.SUBCATEGORY_DELETED, "Subcategoria deletada não pode ser consultada");
        }

        return s;
    }

    @TenantReadOnlyTx
    public List<Subcategory> findAll() {
        return tenantSubcategoryRepository.findNotDeleted();
    }

    @TenantReadOnlyTx
    public List<Subcategory> findActive() {
        return tenantSubcategoryRepository.findActiveNotDeleted();
    }

    @TenantReadOnlyTx
    public List<Subcategory> findByCategoryId(Long categoryId) {
        if (categoryId == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED);
        return tenantSubcategoryRepository.findActiveNotDeletedByCategoryId(categoryId);
    }

    @TenantReadOnlyTx
    public List<Subcategory> findByCategoryIdAdmin(Long categoryId, boolean includeDeleted, boolean includeInactive) {
        if (categoryId == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório");
        return tenantSubcategoryRepository.findByCategoryWithFlags(categoryId, includeDeleted, includeInactive);
    }

    @TenantReadOnlyTx
    public List<Subcategory> findByCategoryIdNotDeleted(Long categoryId) {
        if (categoryId == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório");
        return tenantSubcategoryRepository.findNotDeletedByCategoryId(categoryId);
    }

    // =========================================================
    // WRITE (multi-repo => TenantSchemaUnitOfWork)
    // =========================================================

    public Subcategory create(Long categoryId, Subcategory req) {
        if (categoryId == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório");
        if (req == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_REQUIRED);
        if (!StringUtils.hasText(req.getName())) {
            throw new ApiException(ApiErrorCode.SUBCATEGORY_NAME_REQUIRED, "name é obrigatório");
        }

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Category category = tenantCategoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada: " + categoryId));

            if (category.isDeleted()) {
                throw new ApiException(ApiErrorCode.CATEGORY_DELETED);
            }

            String name = req.getName().trim();

            tenantSubcategoryRepository.findNotDeletedByCategoryIdAndNameIgnoreCase(categoryId, name)
                    .ifPresent(existing -> {
                        throw new ApiException(ApiErrorCode.SUBCATEGORY_ALREADY_EXISTS,
                                "Subcategoria já existe na categoria " + categoryId + ": " + name);
                    });

            Subcategory sub = new Subcategory();
            sub.setCategory(category);
            sub.setName(name);
            sub.setDeleted(false);
            sub.setActive(true);

            return tenantSubcategoryRepository.save(sub);
        });
    }

    public Subcategory update(Long id, Subcategory req) {
        if (id == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "id é obrigatório");
        if (req == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_REQUIRED);

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Subcategory existing = tenantSubcategoryRepository.findByIdWithCategory(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada: " + id));

            if (existing.isDeleted()) {
                throw new ApiException(ApiErrorCode.SUBCATEGORY_DELETED);
            }

            if (StringUtils.hasText(req.getName())) {
                String newName = req.getName().trim();
                Long categoryId = existing.getCategory().getId();

                tenantSubcategoryRepository.findNotDeletedByCategoryIdAndNameIgnoreCase(categoryId, newName)
                        .ifPresent(other -> {
                            if (!other.getId().equals(id)) {
                                throw new ApiException(ApiErrorCode.SUBCATEGORY_ALREADY_EXISTS,
                                        "Subcategoria já existe na categoria " + categoryId + ": " + newName);
                            }
                        });

                existing.setName(newName);
            }

            return tenantSubcategoryRepository.save(existing);
        });
    }

    // =========================================================
    // WRITE (single-repo)
    // =========================================================

    @TenantTx
    public Subcategory toggleActive(Long id) {
        if (id == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "id é obrigatório");

        Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND));

        if (sub.isDeleted()) {
            throw new ApiException(ApiErrorCode.SUBCATEGORY_DELETED, "Não é permitido alterar subcategoria deletada");
        }

        sub.setActive(!sub.isActive());
        return tenantSubcategoryRepository.save(sub);
    }

    @TenantTx
    public void softDelete(Long id) {
        if (id == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "id é obrigatório");

        Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND));

        sub.softDelete();
        tenantSubcategoryRepository.save(sub);
    }

    @TenantTx
    public Subcategory restore(Long id) {
        if (id == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "id é obrigatório");

        Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada: " + id));

        sub.restore();
        return tenantSubcategoryRepository.save(sub);
    }

    // =========================================================
    // Helpers
    // =========================================================

    private String requireBoundTenantSchema() {
        String tenantSchema = TenantContext.getOrNull();
        if (tenantSchema == null) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "Operação requer contexto TENANT.");
        }
        return tenantSchema;
    }
}
