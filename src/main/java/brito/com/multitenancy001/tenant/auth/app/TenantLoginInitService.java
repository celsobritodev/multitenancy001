package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginInitCommand;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginCandidateAccount;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginResult;
import brito.com.multitenancy001.tenant.auth.domain.TenantLoginChallenge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantLoginInitService {

    private final TenantLoginChallengeService tenantLoginChallengeService;
    private final TenantLoginCandidateAccountService tenantLoginCandidateAccountService;

    private final AccountResolver accountResolver;
    private final TenantAuthMechanics authMechanics;
    private final TenantAuthAuditRecorder audit;

    public TenantLoginResult loginInit(TenantLoginInitCommand cmd) {

        if (cmd == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Request inválido");
        if (!StringUtils.hasText(cmd.email())) throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório");
        if (!StringUtils.hasText(cmd.password())) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatória");

        final String normalizedEmail = EmailNormalizer.normalizeOrNull(cmd.email());
        if (!StringUtils.hasText(normalizedEmail)) throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email inválido");

        List<TenantLoginCandidateAccount> candidates = tenantLoginCandidateAccountService.findCandidateAccounts(normalizedEmail);

        if (candidates.isEmpty()) {
            audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, normalizedEmail, null, null, null,
                    "{\\\"reason\\\":\\\"no_candidates\\\"}");
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "usuario ou senha invalidos");
        }

        List<TenantLoginCandidateAccount> valid = candidates.stream()
                .filter(c -> {
                    try {
                        AccountSnapshot account = accountResolver.resolveActiveAccountById(c.accountId());
                        return authMechanics.verifyPasswordInTenant(account, normalizedEmail, cmd.password());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();

        if (valid.isEmpty()) {
            audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, normalizedEmail, null, null, null,
                    "{\\\"reason\\\":\\\"invalid_credentials\\\"}");
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "usuario ou senha invalidos");
        }

        if (valid.size() == 1) {
            TenantLoginCandidateAccount only = valid.getFirst();
            AccountSnapshot account = accountResolver.resolveActiveAccountById(only.accountId());

            var jwt = authMechanics.authenticateWithPassword(account, normalizedEmail, cmd.password());

            audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS, normalizedEmail, jwt.userId(), account.id(), account.tenantSchema(),
                    "{\\\"flow\\\":\\\"single_tenant\\\"}");

            return new TenantLoginResult.LoginSuccess(jwt);
        }

        Set<Long> ids = valid.stream().map(TenantLoginCandidateAccount::accountId).collect(java.util.stream.Collectors.toSet());
        UUID challengeId = tenantLoginChallengeService.createChallenge(normalizedEmail, ids);

        List<TenantLoginResult.TenantSelectionRequired.Detail> details = valid.stream()
                .map(v -> new TenantLoginResult.TenantSelectionRequired.Detail(v.accountId(), v.displayName(), v.slug()))
                .toList();

        audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.SUCCESS, normalizedEmail, null, null, null,
                "{\\\"flow\\\":\\\"multi_tenant\\\",\\\"challengeId\\\":\\\"" + challengeId + "\\\"}");

        return new TenantLoginResult.TenantSelectionRequired(challengeId, details);
    }
}
