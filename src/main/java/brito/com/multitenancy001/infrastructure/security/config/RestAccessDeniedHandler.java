package brito.com.multitenancy001.infrastructure.security.config;

import brito.com.multitenancy001.shared.api.error.ApiEnumErrorResponse;
import brito.com.multitenancy001.shared.time.AppClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AppClock appClock;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {

        String message = resolveMessage(accessDeniedException);

        ApiEnumErrorResponse body = ApiEnumErrorResponse.builder()
                .timestamp(appClock.instant())
                .error("FORBIDDEN")
                .message(message)     // ✅ agora devolve a mensagem real quando existir
                .details(null)
                .build();

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String resolveMessage(AccessDeniedException ex) {
        if (ex == null) return "Acesso negado";
        String msg = ex.getMessage();
        if (msg == null) return "Acesso negado";
        msg = msg.trim();
        return msg.isEmpty() ? "Acesso negado" : msg;
    }
}

