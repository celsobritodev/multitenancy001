package brito.com.multitenancy001.shared.api.error;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente especializado em erros de persistência e constraints.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Traduzir violações de integridade para respostas 409 amigáveis.</li>
 *   <li>Mapear constraints conhecidas para campos funcionais.</li>
 *   <li>Extrair o valor conflitante do texto bruto retornado pelo banco.</li>
 * </ul>
 *
 * <p>Diretrizes:</p>
 * <ul>
 *   <li>Evitar mensagens técnicas no payload retornado ao cliente.</li>
 *   <li>Registrar detalhes técnicos apenas em log.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PersistenceExceptionHandlerComponent {

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
     * Retorna o requestId atual do contexto.
     *
     * @return requestId atual ou null
     */
    private UUID requestId() {
        return RequestMetaContext.requestIdOrNull();
    }

    /**
     * Trata violações de integridade do banco de dados.
     *
     * <p>Mapeia constraints conhecidas para erros funcionais mais amigáveis.</p>
     *
     * @param ex exceção de integridade
     * @return response HTTP 409 padronizado
     */
    public ResponseEntity<ApiEnumErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Instant ts = appNow();

        String errorMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (!StringUtils.hasText(errorMessage)) {
            errorMessage = "";
        }

        log.warn(
                "⚠️ DataIntegrityViolationException capturada | requestId={} | detail={}",
                requestId(),
                errorMessage
        );

        if (errorMessage.contains("tax_id_number")) {
            String cnpj = extractValue(errorMessage, "tax_id_number");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error("DUPLICATE_NUMBER")
                            .message("Já existe uma conta com o número informado.")
                            .field("taxIdNumber")
                            .invalidValue(cnpj)
                            .details(new ErrorDetails(requestId(), "Número já cadastrado"))
                            .build()
            );
        }

        if (errorMessage.contains("LoginEmail")) {
            String email = extractValue(errorMessage, "LoginEmail");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error("DUPLICATE_EMAIL")
                            .message("Já existe uma conta com o e-mail informado.")
                            .field("loginEmail")
                            .invalidValue(email)
                            .details(new ErrorDetails(requestId(), "E-mail já cadastrado"))
                            .build()
            );
        }

        if (errorMessage.contains("slug")) {
            String slug = extractValue(errorMessage, "slug");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error("DUPLICATE_SLUG")
                            .message("Já existe uma conta com o slug informado.")
                            .field("slug")
                            .invalidValue(slug)
                            .details(new ErrorDetails(requestId(), "Slug já cadastrado"))
                            .build()
            );
        }

        if (errorMessage.contains("tenant_schema")) {
            String schema = extractValue(errorMessage, "tenant_schema");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error("DUPLICATE_SCHEMA")
                            .message("Já existe um schema com o identificador informado.")
                            .field("tenantSchema")
                            .invalidValue(schema)
                            .details(new ErrorDetails(requestId(), "Schema já existente"))
                            .build()
            );
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("DUPLICATE_ENTRY")
                        .message("Registro duplicado. Verifique os dados informados.")
                        .details(new ErrorDetails(requestId(), "Violação de integridade"))
                        .build()
        );
    }

    /**
     * Extrai o valor associado a uma constraint do texto bruto retornado pelo banco.
     *
     * @param message mensagem bruta da exceção
     * @param fieldName nome do campo/constraint esperado
     * @return valor extraído ou texto fallback
     */
    private String extractValue(String message, String fieldName) {
        try {
            Pattern pattern = Pattern.compile("\\(" + Pattern.quote(fieldName) + "\\)=\\(([^\\)]+)\\)");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }

            Pattern pattern2 = Pattern.compile("Key \\(" + Pattern.quote(fieldName) + "\\)=\\(([^\\)]+)\\)");
            Matcher matcher2 = pattern2.matcher(message);
            if (matcher2.find()) {
                return matcher2.group(1);
            }
        } catch (Exception e) {
            log.debug(
                    "Falha ao extrair valor do erro de constraint | requestId={} | field={} | message={}",
                    requestId(),
                    fieldName,
                    e.getMessage()
            );
        }

        return "não identificado";
    }
}