package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEventAuditService;
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
    private final TenantAuthSupport tenantAuthSupport;

    private final AuthEventAuditService authEventAuditService;

    /**
     * POST /api/tenant/auth/login
     *
     * email + password
     * - se 1 tenant válido (e senha ok): JWT
     * - se >1:
     *      - valida senha em cada tenant candidato
     *      - se validar em 0: BadCredentials
     *      - se validar em 1: JWT direto
     *      - se validar em >1: TENANT_SELECTION_REQUIRED (challenge contém SOMENTE allowedAccountIds)
     */
    public TenantLoginResult loginInit(TenantLoginInitCommand cmd) {

        if (cmd == null) throw new ApiException("INVALID_REQUEST", "Requisição inválida", 400);
        if (!StringUtils.hasText(cmd.email())) throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        if (!StringUtils.hasText(cmd.password())) throw new ApiException("INVALID_LOGIN", "password é obrigatório", 400);

        final String email = normalizeEmailRequired(cmd.email());
        final String password = cmd.password();

        authEventAuditService.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_INIT,
                AuditOutcome.ATTEMPT,
                email,
                null,
                null,
                null,
                "{\"stage\":\"init\"}"
        );

        try {
            // PUBLIC — descobre quais contas (tenants) têm esse email cadastrado
            List<LoginIdentityRow> identities = publicExecutor.inPublic(() ->
                    loginIdentityResolver.findTenantAccountsByEmail(email)
            );

            if (identities == null || identities.isEmpty()) {
                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                        "{\"reason\":\"no_candidates\"}");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            LinkedHashSet<Long> candidateAccountIds = identities.stream()
                    .map(LoginIdentityRow::accountId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (candidateAccountIds.isEmpty()) {
                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                        "{\"reason\":\"empty_candidate_ids\"}");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            // Se só tem 1 conta, autentica direto
            if (candidateAccountIds.size() == 1) {
                Long accountId = candidateAccountIds.iterator().next();
                AccountSnapshot account = accountResolver.resolveActiveAccountById(accountId);

                TenantLoginResult result = tenantAuthSupport.doTenantAuthentication(account, email, password);

                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.SUCCESS, email, null, accountId, account.schemaName(),
                        "{\"mode\":\"single_tenant\"}");

                return result;
            }

            // MULTI: validar password em cada tenant candidato
            LinkedHashSet<Long> allowedAccountIds = new LinkedHashSet<>();

            for (Long accountId : candidateAccountIds) {
                AccountSnapshot account;
                try {
                    account = accountResolver.resolveActiveAccountById(accountId);
                } catch (Exception ex) {
                    continue;
                }

                boolean ok = tenantAuthSupport.verifyPasswordInTenant(account, email, password);
                if (ok) allowedAccountIds.add(accountId);
            }

            if (allowedAccountIds.isEmpty()) {
                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                        "{\"reason\":\"bad_credentials_multi_tenant\"}");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            // Se só 1 tenant validou senha, entra direto nele
            if (allowedAccountIds.size() == 1) {
                Long accountId = allowedAccountIds.iterator().next();
                AccountSnapshot account = accountResolver.resolveActiveAccountById(accountId);

                TenantLoginResult result = tenantAuthSupport.doTenantAuthentication(account, email, password);

                authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.SUCCESS, email, null, accountId, account.schemaName(),
                        "{\"mode\":\"multi_tenant_but_single_match\"}");

                return result;
            }

            // >1 tenant validou: cria challenge SOMENTE com allowedAccountIds
            UUID challengeId = tenantLoginChallengeService.createChallenge(email, allowedAccountIds);

            List<TenantSelectionOptionData> details = new ArrayList<>();
            for (Long id : allowedAccountIds) {
                details.add(tryResolveOption(id));
            }

            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.TENANT_SELECTION_REQUIRED, AuditOutcome.SUCCESS, email, null, null, null,
                    "{\"challengeId\":\"" + challengeId + "\",\"candidates\":" + allowedAccountIds.size() + "}");

            return new TenantLoginResult.TenantSelectionRequired(
                    challengeId.toString(),
                    details
            );

        } catch (BadCredentialsException e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"bad_credentials\"}");
            throw e;
        } catch (ApiException e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"api_exception\",\"code\":\"" + e.getError() + "\"}");
            throw e;
        } catch (Exception e) {
            authEventAuditService.record(AuthDomain.TENANT, AuthEventType.LOGIN_INIT, AuditOutcome.FAILURE, email, null, null, null,
                    "{\"reason\":\"unexpected\"}");
            throw e;
        }
    }

    private static String normalizeEmailRequired(String raw) {
        String email = EmailNormalizer.normalizeOrNull(raw);
        if (!StringUtils.hasText(email)) {
            throw new ApiException("INVALID_LOGIN", "email é obrigatório", 400);
        }
        return email;
    }

    private TenantSelectionOptionData tryResolveOption(Long accountId) {
        if (accountId == null) {
            return new TenantSelectionOptionData(null, "Conta", null);
        }

        try {
            AccountSnapshot a = accountResolver.resolveActiveAccountById(accountId);
            if (a == null) {
                return new TenantSelectionOptionData(accountId, "Conta " + accountId, null);
            }
            return new TenantSelectionOptionData(a.id(), a.displayName(), a.slug());
        } catch (ApiException ex) {
            return new TenantSelectionOptionData(accountId, "Conta " + accountId, null);
        } catch (Exception ex) {
            return new TenantSelectionOptionData(accountId, "Conta " + accountId, null);
        }
    }
}
