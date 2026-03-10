// ================================================================================
// Record: CustomerResponse
// Pacote: brito.com.multitenancy001.tenant.customers.api.dto
// Descrição: DTO de saída para representar um cliente na API.
//            Contém todos os campos expostos, incluindo flags de status.
// ================================================================================

package brito.com.multitenancy001.tenant.customers.api.dto;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
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
        boolean active,
        boolean deleted,
        String notes
) {}