package brito.com.multitenancy001.shared.api.error;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente especializado em falhas de autenticação e autorização.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Traduzir falhas de autenticação para 401 padronizado.</li>
 *   <li>Traduzir falhas de autorização para 403 padronizado.</li>
 *   <li>Garantir resposta amigável ao cliente.</li>
 *   <li>Registrar contexto técnico completo no log.</li>
 * </ul>
 *
 * <p>Observações arquiteturais:</p>
 * <ul>
 *   <li>Não vaza detalhes técnicos de autenticação/autorização para o cliente.</li>
 *   <li>Como o payload {@link ApiEnumErrorResponse} atual não possui campo
 *       {@code requestId} próprio, o rastreamento é devolvido em {@code details}
 *       via {@link ErrorDetails}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityExceptionHandlerComponent {

    private final AppClock appClock;

    /**
     * Retorna o instante atual a partir do clock central da aplicação.
     *
     * @return instante atual da aplicação
     */
    private Instant appNow() {
        return appClock.instant();
    }

    /**
     * Retorna o requestId atual do contexto da request.
     *
     * @return requestId atual ou null
     */
    private UUID requestId() {
        return RequestMetaContext.requestIdOrNull();
    }

    /**
     * Trata falhas de autenticação.
     *
     * @param ex exceção de autenticação
     * @return response HTTP 401 padronizado
     */
    public ResponseEntity<ApiEnumErrorResponse> handleAuthentication(AuthenticationException ex) {
        Instant ts = appNow();

        log.warn(
                "⚠️ Falha de autenticação capturada | requestId={} | message={}",
                requestId(),
                ex == null ? null : ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("INVALID_USER")
                        .message("Usuário ou senha inválidos.")
                        .details(new ErrorDetails(requestId(), "Falha de autenticação"))
                        .build()
        );
    }

    /**
     * Trata acesso negado no fluxo de autorização.
     *
     * @param ex exceção de autorização
     * @return response HTTP 403 padronizado
     */
    public ResponseEntity<ApiEnumErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        Instant ts = appNow();

        log.warn(
                "⚠️ AuthorizationDeniedException capturada | requestId={} | message={}",
                requestId(),
                ex == null ? null : ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ApiErrorCode.FORBIDDEN.name())
                        .message("Acesso negado.")
                        .details(new ErrorDetails(requestId(), "Usuário sem permissão"))
                        .build()
        );
    }

    /**
     * Trata acesso negado por segurança.
     *
     * @param ex exceção de acesso negado
     * @return response HTTP 403 padronizado
     */
    public ResponseEntity<ApiEnumErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        Instant ts = appNow();

        log.warn(
                "⚠️ AccessDeniedException capturada | requestId={} | message={}",
                requestId(),
                ex == null ? null : ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ApiErrorCode.FORBIDDEN.name())
                        .message("Acesso negado.")
                        .details(new ErrorDetails(requestId(), "Usuário sem permissão"))
                        .build()
        );
    }
}