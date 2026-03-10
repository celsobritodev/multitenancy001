// ================================================================================
// Record: CreateCustomerCommand
// Pacote: brito.com.multitenancy001.tenant.customers.app.command
// Descrição: Command para criação de um novo cliente (Customer).
//            Contém apenas dados primitivos, sem lógica de negócio.
// ================================================================================

package brito.com.multitenancy001.tenant.customers.app.command;

public record CreateCustomerCommand(
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
) {
    // Validações de formato (não de negócio) podem ser adicionadas via Bean Validation
    // no controller, se necessário.
}