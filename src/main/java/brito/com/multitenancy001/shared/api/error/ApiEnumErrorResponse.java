package brito.com.multitenancy001.shared.api.error;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record ApiEnumErrorResponse(
        Instant timestamp,
        String error,
        String message,

        // opcional: erros de enum/constraint
        String field,
        String invalidValue,
        List<String> allowedValues,

        // ✅ qualquer payload extra (ex.: lista de tenants para seleção)
        Object details
) {}

