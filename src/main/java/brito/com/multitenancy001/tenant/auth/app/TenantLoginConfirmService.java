package brito.com.multitenancy001.tenant.auth.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginConfirmCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada principal do fluxo de confirmação de login de tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Manter compatibilidade com controllers e chamadores atuais.</li>
 *   <li>Delegar o fluxo real de confirmação para o command service.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Esta classe deve permanecer fina.</li>
 *   <li>Não deve concentrar validação, resolução de conta,
 *       emissão de JWT ou auditoria.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantLoginConfirmService {

    private final TenantLoginConfirmCommandService tenantLoginConfirmCommandService;

    /**
     * Confirma login de tenant a partir de challenge previamente emitido.
     *
     * @param tenantLoginConfirmCommand comando de confirmação
     * @return JWT emitido para a conta selecionada
     */
    public JwtResult loginConfirm(TenantLoginConfirmCommand tenantLoginConfirmCommand) {
        log.info("Delegando loginConfirm para TenantLoginConfirmCommandService.");
        return tenantLoginConfirmCommandService.loginConfirm(tenantLoginConfirmCommand);
    }
}