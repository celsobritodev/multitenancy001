package brito.com.multitenancy001.tenant.categories.api.mapper;

import brito.com.multitenancy001.tenant.categories.api.dto.SubcategoryCreateRequest;
import brito.com.multitenancy001.tenant.categories.api.dto.SubcategoryResponse;
import brito.com.multitenancy001.tenant.categories.api.dto.SubcategoryUpdateRequest;
import brito.com.multitenancy001.tenant.categories.app.command.CreateSubcategoryCommand;
import brito.com.multitenancy001.tenant.categories.app.command.UpdateSubcategoryCommand;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper HTTP <-> Application <-> Domain para Subcategory.
 */
@Component
public class SubcategoryApiMapper {

    /**
     * DTO + path param -> Command.
     */
    public CreateSubcategoryCommand toCreateCommand(Long categoryId, SubcategoryCreateRequest req) {
        //  Service valida e normaliza.
        return new CreateSubcategoryCommand(categoryId, req.name());
    }

    /**
     * DTO -> Command.
     */
    public UpdateSubcategoryCommand toUpdateCommand(SubcategoryUpdateRequest req) {
        //  PUT exige name.
        return new UpdateSubcategoryCommand(req.name());
    }

    /**
     * Domain -> Response.
     */
    public SubcategoryResponse toResponse(Subcategory s) {
        //  categoryId é derivado do relacionamento.
        Long categoryId = (s.getCategory() != null ? s.getCategory().getId() : null);

        return new SubcategoryResponse(
                s.getId(),
                categoryId,
                s.getName(),
                s.isActive(),
                s.isDeleted()
        );
    }

    /**
     * Lista Domain -> Lista Response.
     */
    public List<SubcategoryResponse> toResponseList(List<Subcategory> list) {
        //  map simples.
        return list.stream().map(this::toResponse).toList();
    }
}