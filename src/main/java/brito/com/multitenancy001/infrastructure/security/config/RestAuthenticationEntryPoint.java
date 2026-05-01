package brito.com.multitenancy001.infrastructure.security.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import brito.com.multitenancy001.shared.api.error.ApiEnumErrorResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.api.error.ErrorDetails;
import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.time.AppClock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Entry point HTTP para falhas de autenticação.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Responder 401 com payload amigável e estável.</li>
 *   <li>Registrar log técnico com requestId e contexto HTTP.</li>
 *   <li>Evitar vazamento de detalhes internos de autenticação.</li>
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
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final AppClock appClock;

    /**
     * Responde falhas de autenticação no pipeline de segurança.
     *
     * @param request request HTTP atual
     * @param response response HTTP atual
     * @param authException exceção de autenticação
     * @throws IOException erro de serialização/escrita
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        log.warn(
                "⚠️ AuthenticationEntryPoint acionado | requestId={} | method={} | uri={} | message={}",
                RequestMetaContext.requestIdOrNull(),
                request.getMethod(),
                request.getRequestURI(),
                authException == null ? null : authException.getMessage()
        );

        ApiEnumErrorResponse body = ApiEnumErrorResponse.builder()
                .timestamp(appClock.instant())
                .error(ApiErrorCode.UNAUTHENTICATED.name())
                .message("Não autenticado ou token inválido.")
                .details(new ErrorDetails(
                        RequestMetaContext.requestIdOrNull(),
                        "Autenticação obrigatória"
                ))
                .build();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}