package brito.com.multitenancy001.tenant.application.category;

import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.domain.category.Category;
import brito.com.multitenancy001.tenant.persistence.category.TenantCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantCategoryService {

    private final TenantCategoryRepository categoryRepository;
    private final AppClock appClock;

    // =========================================================
    // READ
    // =========================================================

    @Transactional(readOnly = true)
    public Category findById(Long id) {
        if (id == null) throw new ApiException("CATEGORY_ID_REQUIRED", "id é obrigatório", 400);

        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND", "Categoria não encontrada: " + id, 404));

        if (c.isDeleted()) {
            throw new ApiException("CATEGORY_DELETED", "Categoria deletada não pode ser consultada", 404);
        }

        return c;
    }

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findNotDeleted();
    }

    @Transactional(readOnly = true)
    public List<Category> findActive() {
        return categoryRepository.findNotDeletedActive();
    }

    @Transactional(readOnly = true)
    public List<Category> searchByName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException("CATEGORY_NAME_REQUIRED", "name é obrigatório", 400);
        }
        return categoryRepository.findNotDeletedByNameContainingIgnoreCase(name.trim());
    }

    @Transactional(readOnly = true)
    public List<Category> findWithFlags(boolean includeDeleted, boolean includeInactive) {
        return categoryRepository.findWithFlags(includeDeleted, includeInactive);
    }

    // =========================================================
    // WRITE
    // =========================================================

    @Transactional
    public Category create(Category category) {
        if (category == null) throw new ApiException("CATEGORY_REQUIRED", "payload é obrigatório", 400);
        if (!StringUtils.hasText(category.getName())) {
            throw new ApiException("CATEGORY_NAME_REQUIRED", "name é obrigatório", 400);
        }

        String name = category.getName().trim();

        // uk_categories_name (global) -> valida para evitar 500
        categoryRepository.findNotDeletedByNameIgnoreCase(name)
                .ifPresent(existing -> {
                    throw new ApiException("CATEGORY_NAME_ALREADY_EXISTS", "Categoria já existe: " + name, 409);
                });

        category.setName(name);
        category.setDeleted(false);
        category.setActive(true);

        return categoryRepository.save(category);
    }

    @Transactional
    public Category update(Long id, Category req) {
        if (id == null) throw new ApiException("CATEGORY_ID_REQUIRED", "id é obrigatório", 400);
        if (req == null) throw new ApiException("CATEGORY_REQUIRED", "payload é obrigatório", 400);

        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND", "Categoria não encontrada: " + id, 404));

        if (existing.isDeleted()) {
            throw new ApiException("CATEGORY_DELETED", "Não é permitido alterar categoria deletada", 409);
        }

        if (StringUtils.hasText(req.getName())) {
            String newName = req.getName().trim();

            // valida unicidade
            categoryRepository.findNotDeletedByNameIgnoreCase(newName)
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ApiException("CATEGORY_NAME_ALREADY_EXISTS", "Categoria já existe: " + newName, 409);
                        }
                    });

            existing.setName(newName);
        }

        // active é boolean primitivo -> melhor controlar via endpoint específico (toggle)
        return categoryRepository.save(existing);
    }

    @Transactional
    public Category toggleActive(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND", "Categoria não encontrada: " + id, 404));

        if (category.isDeleted()) {
            throw new ApiException("CATEGORY_DELETED", "Não é permitido alterar categoria deletada", 409);
        }

        category.setActive(!category.isActive());
        return categoryRepository.save(category);
    }

    @Transactional
    public void softDelete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND", "Categoria não encontrada: " + id, 404));

        category.softDelete(appClock.now());
        categoryRepository.save(category);
    }

    @Transactional
    public Category restore(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("CATEGORY_NOT_FOUND", "Categoria não encontrada: " + id, 404));

        category.restore();
        return categoryRepository.save(category);
    }
}
