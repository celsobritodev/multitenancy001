package brito.com.multitenancy001.shared.context;

import java.util.UUID;

public record RequestMeta(
        UUID requestId,
        String method,
        String uri,
        String ip,
        String userAgent
) { }

