package brito.com.multitenancy001.tenant.me.api.dto;

import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateMyProfileRequest(
        @Size(min = 2, max = 100, message = "name deve ter entre 2 e 100 caracteres")
        String name,

        @Size(max = 20, message = "phone deve ter no máximo 20 caracteres")
        String phone,

        @Size(max = 500, message = "avatarUrl deve ter no máximo 500 caracteres")
        @URL(protocol = "https", message = "avatarUrl deve ser uma URL https válida")
        String avatarUrl,

        @Size(max = 20, message = "locale deve ter no máximo 20 caracteres")
        String locale,

        @Size(max = 60, message = "timezone deve ter no máximo 60 caracteres")
        String timezone
) {}

