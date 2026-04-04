package brito.com.multitenancy001.tenant.auth.app;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicSchemaExecutor;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.PublicAccountFinder;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountSnapshot;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityFinder;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityRow;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginInitCommand;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginResult;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantSelectionOptionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de login INIT do Tenant.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Resolve candidatos no PUBLIC via {@link LoginIdentityFinder}.</li>
 *   <li>Se houver apenas uma account válida, autentica e emite JWT direto.</li>
 *   <li>Se houver múltiplas accounts válidas:
 *     <ul>
 *       <li>se apenas uma validar senha, autentica direto;</li>
 *       <li>se mais de uma validar senha, cria challenge e retorna seleção;</li>
 *     </ul>
 *   </li>
 *   <li>Auditoria sempre com details estruturado e serialização centralizada.</li>
 * </ul>
 *
 * <p>Regra crítica:</p>
 * <ul>
 *   <li>Todo acesso ao PUBLIC deve rodar dentro do {@link PublicSchemaExecutor}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantLoginInitService {

    private static final String INVALID_CREDENTIALS_MSG = "usuario ou senha invalidos";

    private final PublicAccountFinder accountResolver;
    private final LoginIdentityFinder loginIdentityResolver;
    private final PublicSchemaExecutor publicExecutor;
    private final TenantLoginChallengeService tenantLoginChallengeService;
    private final TenantAuthMechanics authMechanics;
    private final TenantAuthAuditRecorder audit;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Executa o INIT do login tenant.
     *
     * @param cmd comando de login
     * @return resultado de login direto ou necessidade de seleção
     */
    public TenantLoginResult loginInit(TenantLoginInitCommand cmd) {
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Requisição inválida");
        }
        if (!StringUtils.hasText(cmd.email())) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "email é obrigatório");
        }
        if (!StringUtils.hasText(cmd.password())) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "password é obrigatório");
        }

        final String email = normalizeEmailRequired(cmd.email());
        final String password = cmd.password();

        recordAttempt(email);

        try {
            List<LoginIdentityRow> identities = publicExecutor.inPublic(() ->
                    loginIdentityResolver.findTenantAccountsByEmail(email)
            );

            if (identities == null || identities.isEmpty()) {
                recordFailure(email, "no_candidates");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            LinkedHashSet<Long> candidateAccountIds = identities.stream()
                    .map(LoginIdentityRow::accountId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (candidateAccountIds.isEmpty()) {
                recordFailure(email, "empty_candidate_ids");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            if (candidateAccountIds.size() == 1) {
                Long accountId = candidateAccountIds.iterator().next();

                AccountSnapshot account = publicExecutor.inPublic(() ->
                        accountResolver.resolveActiveAccountById(accountId)
                );

                JwtResult jwt = authMechanics.authenticateWithPassword(account, email, password);

                recordSuccessSingle(email, jwt.userId(), accountId, account.tenantSchema());

                return new TenantLoginResult.LoginSuccess(jwt);
            }

            LinkedHashSet<Long> allowedAccountIds = new LinkedHashSet<>();

            for (Long accountId : candidateAccountIds) {
                AccountSnapshot account = publicExecutor.inPublic(() ->
                        accountResolver.resolveActiveAccountById(accountId)
                );

                boolean ok = authMechanics.verifyPasswordInTenant(account, email, password);
                if (ok) {
                    allowedAccountIds.add(accountId);
                }
            }

            if (allowedAccountIds.isEmpty()) {
                recordFailure(email, "no_password_match");
                throw new BadCredentialsException(INVALID_CREDENTIALS_MSG);
            }

            if (allowedAccountIds.size() == 1) {
                Long accountId = allowedAccountIds.iterator().next();

                AccountSnapshot account = publicExecutor.inPublic(() ->
                        accountResolver.resolveActiveAccountById(accountId)
                );

                JwtResult jwt = authMechanics.authenticateWithPassword(account, email, password);

                recordSuccessResolvedSingle(email, jwt.userId(), accountId, account.tenantSchema());

                return new TenantLoginResult.LoginSuccess(jwt);
            }

            UUID challengeId = tenantLoginChallengeService.createChallenge(email, allowedAccountIds);

            List<TenantSelectionOptionData> options = allowedAccountIds.stream()
                    .map(accountId -> publicExecutor.inPublic(() ->
                            accountResolver.resolveActiveAccountById(accountId)
                    ))
                    .filter(Objects::nonNull)
                    .map(account -> new TenantSelectionOptionData(
                            account.id(),
                            account.displayName(),
                            account.slug()
                    ))
                    .toList();

            recordSelectionRequired(email, challengeId, allowedAccountIds);

            return new TenantLoginResult.TenantSelectionRequired(
                    challengeId.toString(),
                    options
            );

        } catch (BadCredentialsException ex) {
            throw new ApiException(ApiErrorCode.UNAUTHENTICATED, INVALID_CREDENTIALS_MSG);
        }
    }

    /**
     * Registra tentativa do INIT.
     *
     * @param email email normalizado
     */
    private void recordAttempt(String email) {
        audit.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_INIT,
                AuditOutcome.ATTEMPT,
                email,
                null,
                null,
                null,
                toJson(m("stage", "init"))
        );
    }

    /**
     * Registra falha do INIT.
     *
     * @param email email normalizado
     * @param reason motivo lógico
     */
    private void recordFailure(String email, String reason) {
        audit.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_INIT,
                AuditOutcome.FAILURE,
                email,
                null,
                null,
                null,
                toJson(m("reason", reason))
        );
    }

    /**
     * Registra sucesso single-tenant.
     *
     * @param email email normalizado
     * @param userId id do usuário
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     */
    private void recordSuccessSingle(String email, Long userId, Long accountId, String tenantSchema) {
        audit.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_INIT,
                AuditOutcome.SUCCESS,
                email,
                userId,
                accountId,
                tenantSchema,
                toJson(m("mode", "single_tenant"))
        );
    }

    /**
     * Registra sucesso após resolver múltiplas contas para apenas uma válida.
     *
     * @param email email normalizado
     * @param userId id do usuário
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     */
    private void recordSuccessResolvedSingle(String email, Long userId, Long accountId, String tenantSchema) {
        audit.record(
                AuthDomain.TENANT,
                AuthEventType.LOGIN_INIT,
                AuditOutcome.SUCCESS,
                email,
                userId,
                accountId,
                tenantSchema,
                toJson(m("mode", "multi_resolved_single"))
        );
    }

    /**
     * Registra necessidade de seleção de tenant.
     *
     * @param email email normalizado
     * @param challengeId challenge gerado
     * @param allowedAccountIds contas permitidas
     */
    private void recordSelectionRequired(String email, UUID challengeId, Set<Long> allowedAccountIds) {
        audit.record(
                AuthDomain.TENANT,
                AuthEventType.TENANT_SELECTION_REQUIRED,
                AuditOutcome.SUCCESS,
                email,
                null,
                null,
                null,
                toJson(m(
                        "challengeId", challengeId != null ? challengeId.toString() : null,
                        "candidateCount", allowedAccountIds != null ? allowedAccountIds.size() : 0
                ))
        );
    }

    /**
     * Normaliza e valida email obrigatório.
     *
     * @param rawEmail email bruto
     * @return email normalizado
     */
    private static String normalizeEmailRequired(String rawEmail) {
        String normalized = EmailNormalizer.normalizeOrNull(rawEmail);
        if (!StringUtils.hasText(normalized)) {
            throw new ApiException(ApiErrorCode.INVALID_LOGIN, "email inválido");
        }
        return normalized;
    }

    /**
     * Monta mapa ordenado a partir de pares chave/valor.
     *
     * @param kv pares chave/valor
     * @return mapa ordenado
     */
    private Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (kv == null) {
            return map;
        }

        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object key = kv[i];
            Object value = kv[i + 1];
            if (key != null) {
                map.put(String.valueOf(key), value);
            }
        }

        return map;
    }

    /**
     * Serializa details livres para JSON.
     *
     * @param details detalhes estruturados
     * @return json serializado
     */
    private String toJson(Map<String, Object> details) {
        return details == null ? null : jsonDetailsMapper.toJsonNode(details).toString();
    }
}