// ================================================================================
// Record: CustomerUpdateRequest
// Pacote: brito.com.multitenancy001.tenant.customers.api.dto
// Descrição: DTO de entrada para atualização de um cliente.
//            Semântica PATCH: campos null não devem ser alterados.
//            Validações de tamanho são aplicadas apenas se o campo for enviado.
// ================================================================================

package brito.com.multitenancy001.tenant.customers.api.dto;

import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(
        @Size(max = 200, message = "Nome deve ter no máximo 200 caracteres")
        String name,

        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        String email,

        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        String phone,

        @Size(max = 20, message = "Documento deve ter no máximo 20 caracteres")
        String document,

        @Size(max = 10, message = "Tipo de documento deve ter no máximo 10 caracteres")
        String documentType,

        String address,

        @Size(max = 100, message = "Cidade deve ter no máximo 100 caracteres")
        String city,

        @Size(max = 50, message = "Estado deve ter no máximo 50 caracteres")
        String state,

        @Size(max = 20, message = "CEP deve ter no máximo 20 caracteres")
        String zipCode,

        @Size(max = 60, message = "País deve ter no máximo 60 caracteres")
        String country,

        String notes
) {}