package brito.com.multitenancy001.tenant.users.api.validation;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.extern.slf4j.Slf4j;

/**
 * Validador de parâmetros do endpoint de atualização de status de usuário tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Garantir que exatamente um parâmetro de status seja informado.</li>
 *   <li>Padronizar a falha de entrada com {@link ApiException} adequada.</li>
 * </ul>
 *
 * <p><b>Observação:</b></p>
 * <ul>
 *   <li>Este componente valida apenas o contrato HTTP do endpoint.</li>
 *   <li>Não contém regra de negócio de domínio.</li>
 * </ul>
 */
@Component
@Slf4j
public class TenantUserStatusRequestValidator {

    /**
     * Valida que exatamente uma das flags foi informada.
     *
     * @param suspendedByAccount flag de suspensão por conta
     * @param suspendedByAdmin flag de suspensão por admin
     */
    public void validateExactlyOneFlag(Boolean suspendedByAccount, Boolean suspendedByAdmin) {
        log.debug(
                "Validando combinação de flags de status. suspendedByAccount={}, suspendedByAdmin={}",
                suspendedByAccount,
                suspendedByAdmin
        );

        if (suspendedByAccount == null && suspendedByAdmin == null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATUS,
                    "Informe suspendedByAccount ou suspendedByAdmin",
                    400
            );
        }

        if (suspendedByAccount != null && suspendedByAdmin != null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATUS,
                    "Informe apenas um dos parâmetros (suspendedByAccount OU suspendedByAdmin)",
                    400
            );
        }
    }
}