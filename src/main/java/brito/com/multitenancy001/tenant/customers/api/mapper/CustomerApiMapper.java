// ================================================================================
// Classe: CustomerApiMapper
// Pacote: brito.com.multitenancy001.tenant.customers.api.mapper
// Descrição: Mapeia entre DTOs da API, Commands da Application Layer e a entidade
//            de domínio Customer. Centraliza a lógica de conversão.
// ================================================================================

package brito.com.multitenancy001.tenant.customers.api.mapper;

import brito.com.multitenancy001.tenant.customers.api.dto.CustomerCreateRequest;
import brito.com.multitenancy001.tenant.customers.api.dto.CustomerResponse;
import brito.com.multitenancy001.tenant.customers.api.dto.CustomerUpdateRequest;
import brito.com.multitenancy001.tenant.customers.app.command.CreateCustomerCommand;
import brito.com.multitenancy001.tenant.customers.app.command.UpdateCustomerCommand;
import brito.com.multitenancy001.tenant.customers.domain.Customer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomerApiMapper {

    /**
     * Converte um request de criação em um command.
     */
    public CreateCustomerCommand toCreateCommand(CustomerCreateRequest req) {
        return new CreateCustomerCommand(
                req.name(),
                req.email(),
                req.phone(),
                req.document(),
                req.documentType(),
                req.address(),
                req.city(),
                req.state(),
                req.zipCode(),
                req.country(),
                req.notes()
        );
    }

    /**
     * Converte um request de atualização em um command.
     */
    public UpdateCustomerCommand toUpdateCommand(CustomerUpdateRequest req) {
        return new UpdateCustomerCommand(
                req.name(),
                req.email(),
                req.phone(),
                req.document(),
                req.documentType(),
                req.address(),
                req.city(),
                req.state(),
                req.zipCode(),
                req.country(),
                req.notes()
        );
    }

    /**
     * Converte uma entidade de domínio em um response DTO.
     */
    public CustomerResponse toResponse(Customer customer) {
        if (customer == null) return null;
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getDocument(),
                customer.getDocumentType(),
                customer.getAddress(),
                customer.getCity(),
                customer.getState(),
                customer.getZipCode(),
                customer.getCountry(),
                customer.isActive(),
                customer.isDeleted(),
                customer.getNotes()
        );
    }

    /**
     * Converte uma lista de entidades em uma lista de responses.
     */
    public List<CustomerResponse> toResponseList(List<Customer> customers) {
        return customers.stream().map(this::toResponse).toList();
    }
}