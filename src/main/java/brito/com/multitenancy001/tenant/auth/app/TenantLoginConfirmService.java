package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEventAuditService;
import brito.com.multitenancy001.infrastructure.publicschema.auth.TenantLoginChallenge;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginConfirmCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantLoginConfirmService {

    private final TenantLoginChallengeService tenantLoginChallengeService;
    private final AccountResolver accountResolver;
    private final TenantAuthSupport tenantAuthSupport;

    private final AuthEventAuditService authEventAuditService;

    public JwtResult loginConfirm(TenantLoginConfirmCommand cmd) {

        if (cmd == null) throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.challengeId())) throw new ApiException("INVALID_CHALLENGE", "challengeId é obrigatório", 400);

        UUID challengeId;
        try {
            challengeId = UUID.fromString(cmd.challengeId());
        } catch (Exception e) {
            throw new ApiException("INVALID_CHALLENGE", "challengeId inválido", 400);
        }

        TenantLoginChallenge challenge = tenantLoginChallengeService.requireValid(challengeId);
        final String email = challenge.getEmail();

        authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.ATTEMPT, email, null, null, null,
                "{\"challengeId\":\"" + challengeId + "\"}");

        Long accountId = cmd.accountId();
        String slug = StringUtils.hasText(cmd.slug()) ? cmd.slug().trim() : null;

        if (accountId == null && slug == null) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"missing_selection\"}");
            throw new ApiException("INVALID_SELECTION", "Informe accountId ou slug", 400);
        }

        AccountSnapshot account = (accountId != null)
                ? accountResolver.resolveActiveAccountById(accountId)
                : accountResolver.resolveActiveAccountBySlug(slug);

        if (account == null || account.id() == null) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"account_not_found\"}");
            throw new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404);
        }

        Set<Long> allowedAccountIds = challenge.candidateAccountIds();
        if (allowedAccountIds == null || !allowedAccountIds.contains(account.id())) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_CONFIRM, AuditOutcome.FAILURE, email, null, account.id(), account.schemaName(),
                    "{\"reason\":\"account_not_in_challenge\"}");
            throw new ApiException("INVALID_SELECTION", "Conta não pertence ao challenge", 400);
        }

        tenantLoginChallengeService.markUsed(challenge);

        JwtResult jwt = tenantAuthSupport.issueJwtForAccountAndEmail(account, email);

        authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS, email, jwt.userId(), account.id(), account.schemaName(),
                "{\"mode\":\"challenge_confirm\"}");

        return jwt;
    }
}
