package brito.com.multitenancy001.tenant.categories.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.categories.app.command.CreateCategoryCommand;
import brito.com.multitenancy001.tenant.categories.app.command.UpdateCategoryCommand;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.persistence.TenantCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Application Service de Categories (Tenant).
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Executar operações de leitura e escrita do agregado Category</li>
 *   <li>Aplicar regras de negócio (unicidade, soft-delete, validações)</li>
 *   <li>Delegar persistência ao repositório</li>
 * </ul>
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem status HTTP hardcoded</li>
 *   <li>Sem alteração de comportamento</li>
 *   <li>Mensagens preservadas</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantCategoryService {

    private final TenantCategoryRepository tenantCategoryRepository;

    // =========================================================
    // READ
    // =========================================================

    @TenantReadOnlyTx
    public Category findById(Long id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "id é obrigatório");
        }

        Category c = tenantCategoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.CATEGORY_NOT_FOUND,
                        "Categoria não encontrada: " + id
                ));

        if (c.isDeleted()) {
            throw new ApiException(
                    ApiErrorCode.CATEGORY_DELETED,
                    "Categoria deletada não pode ser consultada"
            );
        }

        return c;
    }

    @TenantReadOnlyTx
    public List<Category> findAll() {
        return tenantCategoryRepository.findNotDeleted();
    }

    @TenantReadOnlyTx
    public List<Category> findActive() {
        return tenantCategoryRepository.findNotDeletedActive();
    }

    @TenantReadOnlyTx
    public List<Category> searchByName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ApiErrorCode.CATEGORY_NAME_REQUIRED, "name é obrigatório");
        }
        return tenantCategoryRepository.findNotDeletedByNameContainingIgnoreCase(name.trim());
    }

    @TenantReadOnlyTx
    public List<Category> findWithFlags(boolean includeDeleted, boolean includeInactive) {
        return tenantCategoryRepository.findWithFlags(includeDeleted, includeInactive);
    }

    // =========================================================
    // WRITE
    // =========================================================

    @TenantTx
    public Category create(CreateCategoryCommand cmd) {
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "payload é obrigatório");
        }
        if (!StringUtils.hasText(cmd.name())) {
            throw new ApiException(ApiErrorCode.CATEGORY_NAME_REQUIRED, "name é obrigatório");
        }

        String name = cmd.name().trim();

        tenantCategoryRepository.findNotDeletedByNameIgnoreCase(name)
                .ifPresent(existing -> {
                    throw new ApiException(
                            ApiErrorCode.CATEGORY_NAME_ALREADY_EXISTS,
                            "Categoria já existe: " + name
                    );
                });

        Category category = new Category();
        category.setName(name);
        category.setDeleted(false);
        category.setActive(true);

        return tenantCategoryRepository.save(category);
    }

    @TenantTx
    public Category update(Long id, UpdateCategoryCommand cmd) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "id é obrigatório");
        }
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "payload é obrigatório");
        }

        Category existing = tenantCategoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.CATEGORY_NOT_FOUND,
                        "Categoria não encontrada: " + id
                ));

        if (existing.isDeleted()) {
            throw new ApiException(
                    ApiErrorCode.CATEGORY_DELETED,
                    "Não é permitido alterar categoria deletada"
            );
        }

        if (!StringUtils.hasText(cmd.name())) {
            throw new ApiException(ApiErrorCode.CATEGORY_NAME_REQUIRED, "name é obrigatório");
        }

        String newName = cmd.name().trim();

        tenantCategoryRepository.findNotDeletedByNameIgnoreCase(newName)
                .ifPresent(other -> {
                    if (!other.getId().equals(id)) {
                        throw new ApiException(
                                ApiErrorCode.CATEGORY_NAME_ALREADY_EXISTS,
                                "Categoria já existe: " + newName
                        );
                    }
                });

        existing.setName(newName);

        return tenantCategoryRepository.save(existing);
    }

    @TenantTx
    public Category toggleActive(Long id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "id é obrigatório");
        }

        Category category = tenantCategoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.CATEGORY_NOT_FOUND,
                        "Categoria não encontrada: " + id
                ));

        if (category.isDeleted()) {
            throw new ApiException(
                    ApiErrorCode.CATEGORY_DELETED,
                    "Não é permitido alterar categoria deletada"
            );
        }

        category.setActive(!category.isActive());
        return tenantCategoryRepository.save(category);
    }

    @TenantTx
    public void softDelete(Long id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "id é obrigatório");
        }

        tenantCategoryRepository.findById(id).ifPresent(category -> {
            if (category.isDeleted()) return;
            category.softDelete();
            tenantCategoryRepository.save(category);
        });
    }

    @TenantTx
    public Category restore(Long id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "id é obrigatório");
        }

        Category category = tenantCategoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.CATEGORY_NOT_FOUND,
                        "Categoria não encontrada: " + id
                ));

        category.restore();
        return tenantCategoryRepository.save(category);
    }
}