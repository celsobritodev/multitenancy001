package brito.com.multitenancy001.shared.api.error;

import java.util.UUID;

/**
 * Detalhes seguros para retorno ao cliente.
 */
public record ErrorDetails(
        UUID requestId,
        String description
) {}