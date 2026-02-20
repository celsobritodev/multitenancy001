package brito.com.multitenancy001.tenant.suppliers.api.mapper;

import brito.com.multitenancy001.tenant.suppliers.api.dto.SupplierCreateRequest;
import brito.com.multitenancy001.tenant.suppliers.api.dto.SupplierResponse;
import brito.com.multitenancy001.tenant.suppliers.api.dto.SupplierUpdateRequest;
import brito.com.multitenancy001.tenant.suppliers.app.command.CreateSupplierCommand;
import brito.com.multitenancy001.tenant.suppliers.app.command.UpdateSupplierCommand;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper HTTP <-> Application <-> Domain para Supplier.
 *
 * Regras:
 * - Controller só usa DTO + Mapper
 * - Service só usa Command + Domain
 */
@Component
public class SupplierApiMapper {

    /**
     * Mapeia request HTTP para Command de criação.
     */
    public CreateSupplierCommand toCreateCommand(SupplierCreateRequest req) {
        // Comentário do método: normalização final (trim/uniqueness) acontece no Service.
        return new CreateSupplierCommand(
                req.name(),
                req.contactPerson(),
                req.email(),
                req.phone(),
                req.address(),
                req.document(),
                req.documentType(),
                req.website(),
                req.paymentTerms(),
                req.leadTimeDays(),
                req.rating(),
                req.notes()
        );
    }

    /**
     * Mapeia request HTTP para Command de atualização.
     */
    public UpdateSupplierCommand toUpdateCommand(SupplierUpdateRequest req) {
        // Comentário do método: campos null significam "não alterar" (compat com comportamento atual).
        return new UpdateSupplierCommand(
                req.name(),
                req.contactPerson(),
                req.email(),
                req.phone(),
                req.address(),
                req.document(),
                req.documentType(),
                req.website(),
                req.paymentTerms(),
                req.leadTimeDays(),
                req.rating(),
                req.notes()
        );
    }

    /**
     * Mapeia Domain para Response HTTP.
     */
    public SupplierResponse toResponse(Supplier s) {
        // Comentário do método: expõe flags active/deleted para suportar listagens e admin.
        return new SupplierResponse(
                s.getId(),
                s.getName(),
                s.getContactPerson(),
                s.getEmail(),
                s.getPhone(),
                s.getAddress(),
                s.getDocument(),
                s.getDocumentType(),
                s.getWebsite(),
                s.getPaymentTerms(),
                s.getLeadTimeDays(),
                s.getRating(),
                s.isActive(),
                s.isDeleted(),
                s.getNotes()
        );
    }

    /**
     * Mapeia lista Domain para lista Response.
     */
    public List<SupplierResponse> toResponseList(List<Supplier> list) {
        // Comentário do método: stream simples, sem regras.
        return list.stream().map(this::toResponse).toList();
    }
}