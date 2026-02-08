package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityResolver;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityRow;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginInitCommand;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginResult;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantSelectionOptionData;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantLoginInitService {

    private static final String INVALID_CREDENTIALS_MSG = "usuario ou senha invalidos";

    private final AccountResolver accountResolver;
    private final LoginIdentityResolver loginIdentityResolver;
    private final PublicExecutor publicExecutor;

    private final TenantLoginChallengeService tenantLoginChallengeService;
    private final TenantAuthMechanics authMechanics;

    private final TenantAuthAuditRecorder audit;

    public TenantLoginResult loginInit(TenantLoginInitCommand cmd) {

        if (cmd == null) throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.email())) throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        if (!StringUtils.hasText(cmd.password())) throw new ApiException("INVALID_LOGIN", "password é obrigatório", 400);

        final String email = normalizeEmailRequired(cmd.email());
        final String password = cmd.password();

        audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.ATTEMPT, email, null, null, null,
                "{\"stage\":\"init\"}");

        try {
            List<LoginIdentityRow> identities = publicExecutor.inPublic(() ->
                    loginIdentityResolver.findTenantAccountsByEmail(email)
            );

            if (identities == null || identities.isEmpty()) {
                audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                        "{\"reason\":\"no_candidates\"}");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            LinkedHashSet<Long> candidateAccountIds = identities.stream()
                    .map(LoginIdentityRow::accountId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (candidateAccountIds.isEmpty()) {
                audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                        "{\"reason\":\"empty_candidate_ids\"}");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            // 1 tenant -> autentica direto
            if (candidateAccountIds.size() == 1) {
                Long accountId = candidateAccountIds.iterator().next();
                AccountSnapshot account = accountResolver.resolveActiveAccountById(accountId);

                var jwt = authMechanics.authenticateWithPassword(account, email, password);

                audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.SUCCESS, email, jwt.userId(), accountId, account.schemaName(),
                        "{\"mode\":\"single_tenant\"}");

                return new TenantLoginResult.LoginSuccess(jwt);
            }

            // MULTI: validar password em cada tenant candidato
            LinkedHashSet<Long> allowedAccountIds = new LinkedHashSet<>();

            for (Long accountId : candidateAccountIds) {
                AccountSnapshot account = accountResolver.resolveActiveAccountById(accountId);
                boolean ok = authMechanics.verifyPasswordInTenant(account, email, password);
                if (ok) allowedAccountIds.add(accountId);
            }

            if (allowedAccountIds.isEmpty()) {
                audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                        "{\"reason\":\"no_password_match\"}");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            // se só 1 passou -> autentica e emite jwt
            if (allowedAccountIds.size() == 1) {
                Long accountId = allowedAccountIds.iterator().next();
                AccountSnapshot account = accountResolver.resolveActiveAccountById(accountId);

                var jwt = authMechanics.authenticateWithPassword(account, email, password);

                audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.SUCCESS, email, jwt.userId(), accountId, account.schemaName(),
                        "{\"mode\":\"multi_resolved_single\"}");

                return new TenantLoginResult.LoginSuccess(jwt);
            }

            // >1 passou -> cria challenge com SOMENTE allowedAccountIds
            UUID challengeId = tenantLoginChallengeService.createChallenge(email, allowedAccountIds);

            List<TenantSelectionOptionData> details = identities.stream()
                    .filter(r -> r.accountId() != null && allowedAccountIds.contains(r.accountId()))
                    .map(r -> new TenantSelectionOptionData(r.accountId(), r.displayName(), r.slug()))
                    .toList();

            audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.SUCCESS, email, null, null, null,
                    "{\"mode\":\"tenant_selection_required\",\"challengeId\":\"" + challengeId + "\"}");

            return new TenantLoginResult.TenantSelectionRequired(challengeId.toString(), details);

        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            audit.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"unexpected\"}");
            throw ex;
        }
    }

    private static String normalizeEmailRequired(String email) {
        String normalized = EmailNormalizer.normalizeOrNull(email);
        if (!StringUtils.hasText(normalized)) {
            throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
        }
        return normalized;
    }
}
