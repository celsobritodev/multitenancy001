// ================================================================================
// Record: UpdateCustomerCommand
// Pacote: brito.com.multitenancy001.tenant.customers.app.command
// Descrição: Command para atualização de um cliente existente.
//            Semântica PATCH: campos null não devem ser alterados.
// ================================================================================

package brito.com.multitenancy001.tenant.customers.app.command;

public record UpdateCustomerCommand(
        String name,
        String email,
        String phone,
        String document,
        String documentType,
        String address,
        String city,
        String state,
        String zipCode,
        String country,
        String notes
) {}