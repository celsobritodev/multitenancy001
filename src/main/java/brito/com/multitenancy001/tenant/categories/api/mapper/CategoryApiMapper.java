package brito.com.multitenancy001.tenant.categories.api.mapper;

import brito.com.multitenancy001.tenant.categories.api.dto.CategoryCreateRequest;
import brito.com.multitenancy001.tenant.categories.api.dto.CategoryResponse;
import brito.com.multitenancy001.tenant.categories.api.dto.CategoryUpdateRequest;
import brito.com.multitenancy001.tenant.categories.app.command.CreateCategoryCommand;
import brito.com.multitenancy001.tenant.categories.app.command.UpdateCategoryCommand;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper HTTP <-> Application <-> Domain para Category.
 *
 * Regras:
 * - Controller só usa DTO + Mapper
 * - Service só usa Command + Domain
 */
@Component
public class CategoryApiMapper {

    /**
     * Mapeia request HTTP para Command de criação.
     */
    public CreateCategoryCommand toCreateCommand(CategoryCreateRequest req) {
        // Comentário do método: normalização final acontece no Service (trim/uniqueness).
        return new CreateCategoryCommand(req.name());
    }

    /**
     * Mapeia request HTTP para Command de atualização.
     */
    public UpdateCategoryCommand toUpdateCommand(CategoryUpdateRequest req) {
        // Comentário do método: PUT exige name; regra de unicidade é no Service.
        return new UpdateCategoryCommand(req.name());
    }

    /**
     * Mapeia Domain para Response HTTP.
     */
    public CategoryResponse toResponse(Category c) {
        // Comentário do método: expõe deleted para suportar endpoints admin sem trocar DTO.
        return new CategoryResponse(
                c.getId(),
                c.getName(),
                c.isActive(),
                c.isDeleted()
        );
    }

    /**
     * Mapeia lista Domain para lista Response.
     */
    public List<CategoryResponse> toResponseList(List<Category> list) {
        // Comentário do método: stream simples, sem regras.
        return list.stream().map(this::toResponse).toList();
    }
}