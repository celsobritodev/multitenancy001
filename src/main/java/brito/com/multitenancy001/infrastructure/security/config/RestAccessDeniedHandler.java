package brito.com.multitenancy001.infrastructure.security.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import brito.com.multitenancy001.shared.api.error.ApiEnumErrorResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.api.error.ErrorDetails;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.time.AppClock;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler HTTP para respostas 403 no pipeline de segurança.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Responder 403 com payload estável e amigável.</li>
 *   <li>Registrar log técnico com requestId e contexto HTTP.</li>
 *   <li>Evitar vazamento de detalhes internos de autorização.</li>
 * </ul>
 *
 * <p>Observação:</p>
 * <ul>
 *   <li>O payload atual {@link ApiEnumErrorResponse} não expõe campo próprio
 *       de requestId, então o rastreamento é enviado em {@code details}
 *       via {@link ErrorDetails}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AppClock appClock;

    /**
     * Trata acesso negado no pipeline de segurança.
     *
     * @param request request HTTP atual
     * @param response response HTTP atual
     * @param accessDeniedException exceção de acesso negado
     * @throws IOException erro de serialização/escrita
     * @throws ServletException erro de servlet
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {

        log.warn(
                "⚠️ AccessDeniedHandler acionado | requestId={} | method={} | uri={} | message={}",
                RequestMetaContext.requestIdOrNull(),
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException == null ? null : accessDeniedException.getMessage()
        );

        ApiEnumErrorResponse body = ApiEnumErrorResponse.builder()
                .timestamp(appClock.instant())
                .error(ApiErrorCode.FORBIDDEN.name())
                .message("Acesso negado.")
                .details(new ErrorDetails(
                        RequestMetaContext.requestIdOrNull(),
                        "Usuário sem permissão para acessar este recurso"
                ))
                .build();

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}