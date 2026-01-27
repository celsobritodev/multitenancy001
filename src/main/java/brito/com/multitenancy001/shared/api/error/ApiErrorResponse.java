package brito.com.multitenancy001.shared.api.error;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ApiErrorResponse(
        LocalDateTime timestamp,
        String error,
        String message,
        List<String> details
) {}
