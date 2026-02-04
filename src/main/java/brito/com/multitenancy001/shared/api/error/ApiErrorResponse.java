package brito.com.multitenancy001.shared.api.error;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record ApiErrorResponse(
        Instant timestamp,
        String error,
        String message,
        List<String> details
) {}

