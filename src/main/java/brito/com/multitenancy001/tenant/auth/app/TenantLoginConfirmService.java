package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginConfirmCommand;
import brito.com.multitenancy001.tenant.auth.domain.TenantLoginChallenge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;


/**
 * Application Service responsável por CONFIRMAR login de Tenant quando o INIT retornou ambiguidade.
 *
 * <p>Fluxo suportado:</p>
 * <ul>
 *   <li><b>INIT</b> (email+senha) retorna {@code 409 TENANT_SELECTION_REQUIRED} quando há mais de um tenant válido.</li>
 *   <li>O backend cria um {@code challengeId} (prova temporária) vinculando email + tenants permitidos.</li>
 *   <li><b>CONFIRM</b> recebe {@code challengeId} + {@code slug} (ou {@code accountId}) para selecionar o tenant.</li>
 *   <li>O backend valida o challenge e então emite JWT somente para o tenant escolhido.</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Nunca emite token no INIT quando houver ambiguidade.</li>
 *   <li>O CONFIRM não revalida senha; a validade do challenge prova que a senha já foi checada no INIT.</li>
 *   <li>Deve registrar auditoria de tentativa/sucesso/falha (auth audit) com detalhes do challenge.</li>
 * </ul>
 *
 * <p>Arquitetura:</p>
 * <ul>
 *   <li>Não deve conhecer detalhes de JWT/Spring Security diretamente; delega para {@code TenantAuthMechanics}.</li>
 *   <li>Resolução de conta (slug/accountId) usa camada de leitura do Control Plane (public schema).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TenantLoginConfirmService {

    private final TenantLoginChallengeService tenantLoginChallengeService;
    private final AccountResolver accountResolver;
    private final TenantAuthMechanics authMechanics;
    private final TenantAuthAuditRecorder audit;

    public JwtResult loginConfirm(TenantLoginConfirmCommand cmd) {

        if (cmd == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.challengeId())) throw new ApiException(ApiErrorCode.INVALID_CHALLENGE, "challengeId é obrigatório", 400);

        final UUID challengeId;
        try {
            challengeId = UUID.fromString(cmd.challengeId());
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INVALID_CHALLENGE, "challengeId inválido", 400);
        }

        TenantLoginChallenge challenge = tenantLoginChallengeService.requireValid(challengeId);
        final String email = challenge.email();

        audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.ATTEMPT, email, null, null, null,
                "{\"challengeId\":\"" + challengeId + "\"}");

        Long accountId = cmd.accountId();
        String slug = StringUtils.hasText(cmd.slug()) ? cmd.slug().trim() : null;

        if (accountId == null && slug == null) {
            audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"missing_selection\"}");
            throw new ApiException(ApiErrorCode.INVALID_SELECTION, "Informe accountId ou slug", 400);
        }

        AccountSnapshot account = (accountId != null)
                ? accountResolver.resolveActiveAccountById(accountId)
                : accountResolver.resolveActiveAccountBySlug(slug);

        if (account == null || account.id() == null) {
            audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"account_not_found\"}");
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404);
        }

        Set<Long> allowedAccountIds = challenge.candidateAccountIds();
        if (allowedAccountIds == null || !allowedAccountIds.contains(account.id())) {
            audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.FAILURE, email, null, account.id(), account.tenantSchema(),
                    "{\"reason\":\"account_not_in_challenge\"}");
            throw new ApiException(ApiErrorCode.INVALID_SELECTION, "Conta não pertence ao challenge", 400);
        }

        tenantLoginChallengeService.markUsed(challengeId);

        JwtResult jwt = authMechanics.issueJwtForAccountAndEmail(account, email);

        audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS, email, jwt.userId(), account.id(), account.tenantSchema(),
                "{\"mode\":\"challenge_confirm\"}");

        return jwt;
    }
}
