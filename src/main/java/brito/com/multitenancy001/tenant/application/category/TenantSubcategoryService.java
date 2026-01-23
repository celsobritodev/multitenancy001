package brito.com.multitenancy001.tenant.application.category;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.domain.category.Category;
import brito.com.multitenancy001.tenant.domain.category.Subcategory;
import brito.com.multitenancy001.tenant.persistence.category.TenantCategoryRepository;
import brito.com.multitenancy001.tenant.persistence.category.TenantSubcategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubcategoryService {

    private final TenantSubcategoryRepository subcategoryRepository;
    private final TenantCategoryRepository categoryRepository;
    private final AppClock appClock;

    // =========================================================
    // READ
    // =========================================================

    @Transactional(readOnly = true)
    public Subcategory findById(Long id) {
        if (id == null) throw new ApiException("SUBCATEGORY_ID_REQUIRED", "id é obrigatório", 400);

        Subcategory s = subcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND", "Subcategoria não encontrada: " + id, 404));

        if (s.isDeleted()) {
            throw new ApiException("SUBCATEGORY_DELETED", "Subcategoria deletada não pode ser consultada", 404);
        }

        return s;
    }

    @Transactional(readOnly = true)
    public List<Subcategory> findAll() {
        return subcategoryRepository.findNotDeleted();
    }

    @Transactional(readOnly = true)
    public List<Subcategory> findActive() {
        return subcategoryRepository.findActiveNotDeleted();
    }

    @Transactional(readOnly = true)
    public List<Subcategory> findByCategoryId(Long categoryId) {
        if (categoryId == null) throw new ApiException("CATEGORY_ID_REQUIRED", "categoryId é obrigatório", 400);
        return subcategoryRepository.findActiveNotDeletedByCategoryId(categoryId);
    }

    @Transactional(readOnly = true)
    public List<Subcategory> findByCategoryIdAdmin(Long categoryId, boolean includeDeleted, boolean includeInactive) {
        if (categoryId == null) throw new ApiException("CATEGORY_ID_REQUIRED", "categoryId é obrigatório", 400);
        return subcategoryRepository.findByCategoryWithFlags(categoryId, includeDeleted, includeInactive);
    }

    // =========================================================
    // WRITE
    // =========================================================

    @Transactional
    public Subcategory create(Long categoryId, Subcategory req) {
        if (categoryId == null) throw new ApiException("CATEGORY_ID_REQUIRED", "categoryId é obrigatório", 400);
        if (req == null) throw new ApiException("SUBCATEGORY_REQUIRED", "payload é obrigatório", 400);
        if (!StringUtils.hasText(req.getName())) {
            throw new ApiException("SUBCATEGORY_NAME_REQUIRED", "name é obrigatório", 400);
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND", "Categoria não encontrada: " + categoryId, 404));

        if (category.isDeleted()) {
            throw new ApiException("CATEGORY_DELETED", "Não é permitido criar subcategoria em categoria deletada", 409);
        }

        String name = req.getName().trim();

        // uk_subcategories_name_category (category_id, name)
        subcategoryRepository.findNotDeletedByCategoryIdAndNameIgnoreCase(categoryId, name)
                .ifPresent(existing -> {
                    throw new ApiException("SUBCATEGORY_ALREADY_EXISTS",
                            "Subcategoria já existe na categoria " + categoryId + ": " + name, 409);
                });

        Subcategory sub = new Subcategory();
        sub.setCategory(category);
        sub.setName(name);
        sub.setDeleted(false);
        sub.setActive(true);

        return subcategoryRepository.save(sub);
    }

    @Transactional
    public Subcategory update(Long id, Subcategory req) {
        if (id == null) throw new ApiException("SUBCATEGORY_ID_REQUIRED", "id é obrigatório", 400);
        if (req == null) throw new ApiException("SUBCATEGORY_REQUIRED", "payload é obrigatório", 400);

        Subcategory existing = subcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND", "Subcategoria não encontrada: " + id, 404));

        if (existing.isDeleted()) {
            throw new ApiException("SUBCATEGORY_DELETED", "Não é permitido alterar subcategoria deletada", 409);
        }

        if (StringUtils.hasText(req.getName())) {
            String newName = req.getName().trim();
            Long categoryId = existing.getCategory().getId();

            subcategoryRepository.findNotDeletedByCategoryIdAndNameIgnoreCase(categoryId, newName)
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ApiException("SUBCATEGORY_ALREADY_EXISTS",
                                    "Subcategoria já existe na categoria " + categoryId + ": " + newName, 409);
                        }
                    });

            existing.setName(newName);
        }

        return subcategoryRepository.save(existing);
    }

    @Transactional
    public Subcategory toggleActive(Long id) {
        Subcategory sub = subcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND", "Subcategoria não encontrada: " + id, 404));

        if (sub.isDeleted()) {
            throw new ApiException("SUBCATEGORY_DELETED", "Não é permitido alterar subcategoria deletada", 409);
        }

        sub.setActive(!sub.isActive());
        return subcategoryRepository.save(sub);
    }

    @Transactional
    public void softDelete(Long id) {
        Subcategory sub = subcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND", "Subcategoria não encontrada: " + id, 404));

        sub.softDelete(appClock.now());
        subcategoryRepository.save(sub);
    }

    @Transactional
    public Subcategory restore(Long id) {
        Subcategory sub = subcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ApiException("SUBCATEGORY_NOT_FOUND", "Subcategoria não encontrada: " + id, 404));

        sub.restore();
        return subcategoryRepository.save(sub);
    }
    
    @Transactional(readOnly = true)
    public List<Subcategory> findByCategoryIdNotDeleted(Long categoryId) {
        if (categoryId == null) throw new ApiException("CATEGORY_ID_REQUIRED", "categoryId é obrigatório", 400);
        return subcategoryRepository.findNotDeletedByCategoryId(categoryId);
    }

}
