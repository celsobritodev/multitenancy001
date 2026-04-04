package brito.com.multitenancy001.tenant.auth.app;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Fachada fina do fluxo de password reset do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Preservar contrato estável usado pelos controllers.</li>
 *   <li>Delegar a execução real ao command service.</li>
 * </ul>
 *
 * <p>Observação:</p>
 * <ul>
 *   <li>Esta classe não concentra regra de negócio pesada.</li>
 *   <li>Validação, auditoria e execução ficam em serviços especializados.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TenantPasswordResetService {

    private final TenantPasswordResetCommandService tenantPasswordResetCommandService;

    /**
     * Gera token de reset de senha para usuário tenant ativo.
     *
     * @param slug slug da account
     * @param email email do usuário
     * @return token de reset
     */
    public String generatePasswordResetToken(String slug, String email) {
        return tenantPasswordResetCommandService.generatePasswordResetToken(slug, email);
    }

    /**
     * Executa reset de senha a partir de token válido.
     *
     * @param token token de reset
     * @param newPassword nova senha
     */
    public void resetPasswordWithToken(String token, String newPassword) {
        tenantPasswordResetCommandService.resetPasswordWithToken(token, newPassword);
    }
}