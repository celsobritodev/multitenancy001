package brito.com.multitenancy001.shared.context;

import org.slf4j.MDC;

import java.util.UUID;

public final class RequestMetaContext {

    private static final ThreadLocal<RequestMeta> HOLDER = new ThreadLocal<>();

    private RequestMetaContext() { }

    public static void set(RequestMeta meta) {
        HOLDER.set(meta);
        if (meta != null && meta.requestId() != null) {
            MDC.put("requestId", meta.requestId().toString());
        }
    }

    public static RequestMeta getOrNull() {
        return HOLDER.get();
    }

    public static UUID requestIdOrNull() {
        RequestMeta meta = HOLDER.get();
        return meta == null ? null : meta.requestId();
    }

    public static void clear() {
        try { HOLDER.remove(); } catch (Exception ignore) {}
        try { MDC.remove("requestId"); } catch (Exception ignore) {}
    }
}
