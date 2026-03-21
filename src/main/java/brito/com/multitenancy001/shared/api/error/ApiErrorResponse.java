package brito.com.multitenancy001.shared.api.error;

import java.time.Instant;

import lombok.Builder;

/**
 * Payload padronizado de erro da API.
 *
 * <p>Este response é utilizado para devolver erros funcionais, de validação,
 * de domínio e demais falhas controladas da aplicação em um formato estável.</p>
 *
 * <p>Objetivos principais:</p>
 * <ul>
 *   <li>Padronizar o shape de erro consumido por frontend e integrações.</li>
 *   <li>Permitir que suítes E2E validem o campo {@code code} diretamente na raiz.</li>
 *   <li>Suportar metadados úteis como status HTTP e path da requisição.</li>
 *   <li>Suportar detalhes flexíveis via campo {@code details}.</li>
 * </ul>
 *
 * <p>Observação importante:</p>
 * <ul>
 *   <li>O campo {@code details} é propositalmente do tipo {@link Object},
 *       pois alguns fluxos devolvem {@code List<String>} e outros devolvem
 *       estruturas mais ricas vindas de {@code ApiException}.</li>
 * </ul>
 *
 * @param timestamp instante de geração do erro
 * @param status status HTTP associado
 * @param error nome textual/legado do erro
 * @param code código funcional/canônico do erro
 * @param category categoria funcional do erro
 * @param message mensagem principal do erro
 * @param details detalhes adicionais do erro
 * @param path path HTTP da requisição
 */
@Builder
public record ApiErrorResponse(
        Instant timestamp,
        Integer status,
        String error,
        String code,
        String category,
        String message,
        Object details,
        String path
) {
}