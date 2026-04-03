package brito.com.multitenancy001.tenant.auth.app;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginConfirmCommand;
import brito.com.multitenancy001.tenant.auth.domain.TenantLoginChallenge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comando responsável por confirmar login de tenant
 * quando o INIT retornou ambiguidade.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar comando e challengeId.</li>
 *   <li>Carregar challenge válido.</li>
 *   <li>Resolver a conta selecionada por slug ou accountId.</li>
 *   <li>Validar se a conta pertence ao challenge.</li>
 *   <li>Marcar challenge como usado.</li>
 *   <li>Emitir JWT para a conta escolhida.</li>
 *   <li>Registrar auditoria de tentativa, falha e sucesso.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantLoginConfirmCommandService {

    private final TenantLoginChallengeService tenantLoginChallengeService;
    private final TenantLoginSelectionResolver tenantLoginSelectionResolver;
    private final TenantAuthMechanics tenantAuthMechanics;
    private final TenantLoginConfirmAuditService tenantLoginConfirmAuditService;
    private final TenantLoginConfirmSupport tenantLoginConfirmSupport;

    /**
     * Confirma login de tenant usando challenge e seleção de conta.
     *
     * @param tenantLoginConfirmCommand comando de confirmação
     * @return JWT emitido para a conta selecionada
     */
    public JwtResult loginConfirm(TenantLoginConfirmCommand tenantLoginConfirmCommand) {
        if (tenantLoginConfirmCommand == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Requisição inválida");
        }

        if (!StringUtils.hasText(tenantLoginConfirmCommand.challengeId())) {
            throw new ApiException(ApiErrorCode.INVALID_CHALLENGE, "challengeId é obrigatório");
        }

        final UUID challengeId = tenantLoginConfirmSupport.parseChallengeId(tenantLoginConfirmCommand.challengeId());

        TenantLoginChallenge tenantLoginChallenge = tenantLoginChallengeService.requireValid(challengeId);
        final String email = tenantLoginChallenge.email();

        tenantLoginConfirmAuditService.recordAttempt(email, challengeId);

        Long accountId = tenantLoginConfirmCommand.accountId();
        String slug = StringUtils.hasText(tenantLoginConfirmCommand.slug())
                ? tenantLoginConfirmCommand.slug().trim()
                : null;

        if (accountId == null && slug == null) {
            tenantLoginConfirmAuditService.recordFailure(email, null, null, "missing_selection");
            throw new ApiException(ApiErrorCode.INVALID_SELECTION, "Informe accountId ou slug");
        }

        AccountSnapshot accountSnapshot = tenantLoginSelectionResolver.resolveSelectedAccount(accountId, slug);

        if (accountSnapshot == null || accountSnapshot.id() == null) {
            tenantLoginConfirmAuditService.recordFailure(email, null, null, "account_not_found");
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada");
        }

        validateAccountBelongsToChallenge(tenantLoginChallenge, accountSnapshot, email);

        tenantLoginChallengeService.markUsed(challengeId);

        JwtResult jwtResult = tenantAuthMechanics.issueJwtForAccountAndEmail(accountSnapshot, email);

        tenantLoginConfirmAuditService.recordSuccess(
                email,
                jwtResult.userId(),
                accountSnapshot.id(),
                accountSnapshot.tenantSchema()
        );

        log.info("✅ Login confirmado com sucesso | email={} | accountId={} | tenantSchema={}",
                email,
                accountSnapshot.id(),
                accountSnapshot.tenantSchema());

        return jwtResult;
    }

    /**
     * Valida se a conta escolhida pertence ao conjunto de contas permitido pelo challenge.
     *
     * @param tenantLoginChallenge challenge válido
     * @param accountSnapshot conta resolvida
     * @param email email autenticado no INIT
     */
    private void validateAccountBelongsToChallenge(
            TenantLoginChallenge tenantLoginChallenge,
            AccountSnapshot accountSnapshot,
            String email
    ) {
        Set<Long> allowedAccountIds = tenantLoginChallenge.candidateAccountIds();

        if (allowedAccountIds == null || !allowedAccountIds.contains(accountSnapshot.id())) {
            tenantLoginConfirmAuditService.recordFailure(
                    email,
                    accountSnapshot.id(),
                    accountSnapshot.tenantSchema(),
                    "account_not_in_challenge"
            );
            throw new ApiException(ApiErrorCode.INVALID_SELECTION, "Conta não pertence ao challenge");
        }
    }
}