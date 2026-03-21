package brito.com.multitenancy001.tenant.users.api.dto;

import java.util.LinkedHashSet;

import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * DTO de request para criação de usuário no contexto tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Representar o payload HTTP de criação de usuário.</li>
 *   <li>Aplicar validações sintáticas básicas de entrada.</li>
 *   <li>Normalizar campos textuais simples ainda no boundary HTTP.</li>
 *   <li>Garantir que a coleção de permissões nunca seja nula.</li>
 * </ul>
 *
 * <p><b>Observações:</b></p>
 * <ul>
 *   <li>O login do usuário tenant é por e-mail.</li>
 *   <li>{@code locale} e {@code timezone} são opcionais; defaults finais
 *       continuam sendo responsabilidade do service.</li>
 *   <li>{@code mustChangePassword} é opcional.</li>
 *   <li>{@code permissions} permanece tipado com {@link TenantPermission}.</li>
 * </ul>
 */
@Builder
public record TenantUserCreateRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        String name,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        @Size(max = 150, message = "Email não pode exceder 150 caracteres")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Pattern(
                regexp = ValidationPatterns.PASSWORD_PATTERN,
                message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas, números e caracteres especiais"
        )
        String password,

        @NotNull(message = "Role é obrigatória")
        TenantRole role,

        LinkedHashSet<@NotNull(message = "Permission não pode ser null") TenantPermission> permissions,

        @Pattern(
                regexp = ValidationPatterns.PHONE_PATTERN,
                message = "Telefone inválido"
        )
        @Size(max = 20, message = "Telefone não pode exceder 20 caracteres")
        String phone,

        @Size(max = 500, message = "URL do avatar não pode exceder 500 caracteres")
        String avatarUrl,

        @Size(max = 10, message = "Locale não pode exceder 10 caracteres")
        String locale,

        @Size(max = 50, message = "Timezone não pode exceder 50 caracteres")
        String timezone,

        Boolean mustChangePassword,

        EntityOrigin origin

) {

    /**
     * Construtor canônico do record com normalizações leves.
     *
     * <p><b>Regras:</b></p>
     * <ul>
     *   <li>Campos textuais são trimados quando informados.</li>
     *   <li>{@code permissions} nunca fica nulo.</li>
     * </ul>
     */
    public TenantUserCreateRequest {
        name = trimToNull(name);
        email = trimToNull(email);
        phone = trimToNull(phone);
        avatarUrl = trimToNull(avatarUrl);
        locale = trimToNull(locale);
        timezone = trimToNull(timezone);
        permissions = permissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions);
    }

    /**
     * Normaliza string para {@code null} quando vazia após trim.
     *
     * @param value valor bruto
     * @return valor normalizado
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}