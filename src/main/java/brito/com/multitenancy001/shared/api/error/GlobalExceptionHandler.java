package brito.com.multitenancy001.shared.api.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import brito.com.multitenancy001.shared.domain.DomainException;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler global de exceções da API.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar um único ponto global de integração HTTP.</li>
 *   <li>Delegar a tradução real de exceções para supports especializados.</li>
 *   <li>Manter payloads e status HTTP estáveis para frontend, integrações e E2E.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Esta classe atua como fachada fina.</li>
 *   <li>A lógica real de tradução fica distribuída por tipo de responsabilidade.</li>
 * </ul>
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ApiExceptionHandlerComponent apiExceptionHandlerSupport;
    private final ValidationExceptionHandlerComponent validationExceptionHandlerSupport;
    private final SecurityExceptionHandlerComponent securityExceptionHandlerSupport;
    private final PersistenceExceptionHandlerComponent persistenceExceptionHandlerSupport;

    /**
     * Trata falhas de parsing do corpo da requisição.
     *
     * @param ex exceção lançada pelo parser HTTP/Jackson
     * @return response HTTP 400 padronizado
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return validationExceptionHandlerSupport.handleNotReadable(ex);
    }

    /**
     * Trata violações de integridade do banco de dados.
     *
     * @param ex exceção de integridade
     * @return response HTTP 409 padronizado
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return persistenceExceptionHandlerSupport.handleDataIntegrityViolation(ex);
    }

    /**
     * Trata exceções padronizadas da aplicação.
     *
     * @param ex exceção funcional da aplicação
     * @param request request HTTP atual
     * @return response HTTP com payload padronizado
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return apiExceptionHandlerSupport.handleApi(ex, request);
    }

    /**
     * Trata erros de validação de bean validation.
     *
     * @param ex exceção de validação
     * @param request request HTTP atual
     * @return response HTTP 400 padronizado
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        return validationExceptionHandlerSupport.handleValidationExceptions(ex, request);
    }

    /**
     * Trata ausência de parâmetro obrigatório de request.
     *
     * @param ex exceção de parâmetro ausente
     * @return response HTTP 400 padronizado
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleMissingRequestParam(MissingServletRequestParameterException ex) {
        return validationExceptionHandlerSupport.handleMissingRequestParam(ex);
    }

    /**
     * Trata mismatch de tipo em path variable ou request param.
     *
     * @param ex exceção de tipo inválido
     * @return response HTTP 400 padronizado
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validationExceptionHandlerSupport.handleTypeMismatch(ex);
    }

    /**
     * Trata falhas de autenticação.
     *
     * @param ex exceção de autenticação
     * @return response HTTP 401 padronizado
     */
    @ExceptionHandler({
            BadCredentialsException.class,
            InternalAuthenticationServiceException.class,
            AuthenticationException.class
    })
    public ResponseEntity<ApiEnumErrorResponse> handleAuthentication(AuthenticationException ex) {
        return securityExceptionHandlerSupport.handleAuthentication(ex);
    }

    /**
     * Trata acesso negado no fluxo de autorização.
     *
     * @param ex exceção de autorização
     * @return response HTTP 403 padronizado
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return securityExceptionHandlerSupport.handleAuthorizationDenied(ex);
    }

    /**
     * Trata acesso negado por segurança.
     *
     * @param ex exceção de acesso negado
     * @return response HTTP 403 padronizado
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return securityExceptionHandlerSupport.handleAccessDenied(ex);
    }

    /**
     * Trata violações de regra de domínio.
     *
     * @param ex exceção de domínio
     * @param request request HTTP atual
     * @return response HTTP 400 padronizado
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        return apiExceptionHandlerSupport.handleDomainException(ex, request);
    }

    /**
     * Trata exceções inesperadas.
     *
     * @param ex exceção não tratada
     * @return response HTTP 500 padronizado
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnumErrorResponse> handleGeneric(Exception ex) {
        return apiExceptionHandlerSupport.handleGeneric(ex);
    }
}