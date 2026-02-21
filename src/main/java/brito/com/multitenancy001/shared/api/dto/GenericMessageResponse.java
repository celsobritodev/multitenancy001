package brito.com.multitenancy001.shared.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Response genérica padronizada para respostas "mensagem".
 *
 * Motivação:
 * - Evitar ResponseEntity<String> com mensagens soltas (contrato instável).
 * - Padronizar respostas simples (ex.: "ok", "token gerado", "senha redefinida").
 */
public record GenericMessageResponse(

        @NotBlank
        String message

) {
}