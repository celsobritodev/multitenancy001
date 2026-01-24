package brito.com.multitenancy001.tenant.api.dto.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantMeUpdateRequest(

        @NotBlank(message = "name é obrigatório")
        @Size(min = 2, max = 100, message = "name deve ter entre 2 e 100 caracteres")
        String name,

        @Size(max = 20, message = "phone deve ter no máximo 20 caracteres")
        String phone,

        @Size(max = 20, message = "locale deve ter no máximo 20 caracteres")
        String locale,

        @Size(max = 60, message = "timezone deve ter no máximo 60 caracteres")
        String timezone
) {}
