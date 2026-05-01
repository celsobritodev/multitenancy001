package brito.com.multitenancy001.infrastructure.publicschema;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;

/**
 * Serviço JDBC responsável por garantir identidades de login no schema público.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Persistir ou atualizar identidades tenant no schema público.</li>
 *   <li>Executar a operação dentro de transação PUBLIC requiresNew.</li>
 *   <li>Registrar logs técnicos sem vazar detalhes internos ao cliente.</li>
 * </ul>
 *
 * <p>Diretrizes:</p>
 * <ul>
 *   <li>Não devolver mensagem técnica de banco em {@link ApiException}.</li>
 *   <li>Manter logs detalhados para troubleshooting.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginIdentityJdbcService implements LoginIdentityService {

    private static final String SQL_ENSURE_TENANT = """
        insert into public.login_identities (email, subject_type, account_id, subject_id)
        values (?, ?, ?, ?)
        on conflict (email, account_id) where subject_type = 'TENANT_ACCOUNT'
        do update set
            email = excluded.email,
            subject_id = excluded.subject_id
        """;

    private final JdbcTemplate jdbcTemplate;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    /**
     * Loga inicialização do componente.
     */
    @PostConstruct
    public void init() {
        log.info("✅ LoginIdentityJdbcService inicializado");
    }

    /**
     * Garante identidade tenant no schema público.
     *
     * @param email email da identidade
     * @param accountId id da conta
     */
    @Override
    public void ensureTenantIdentity(String email, Long accountId) {
        log.info("🔐 ensureTenantIdentity iniciado | email={} | accountId={}", email, accountId);

        if (accountId == null || email == null || email.isBlank()) {
            log.error("❌ Parâmetros inválidos em ensureTenantIdentity | email={} | accountId={}", email, accountId);
            throw new ApiException(
                    ApiErrorCode.INTERNAL_ERROR,
                    "Falha ao garantir identidade de login do tenant."
            );
        }

        try {
            log.info("🔄 Iniciando transação PUBLIC requiresNew | email={} | accountId={}", email, accountId);

            publicSchemaUnitOfWork.requiresNew(() -> {
                log.info("📝 Executando JDBC update | email={} | accountId={}", email, accountId);

                try {
                    int rows = jdbcTemplate.update(
                            SQL_ENSURE_TENANT,
                            email,
                            "TENANT_ACCOUNT",
                            accountId,
                            accountId
                    );

                    log.info("✅ JDBC update executado | email={} | accountId={} | rows={}", email, accountId, rows);
                    return null;

                } catch (DataAccessException e) {
                    log.error(
                            "❌ DataAccessException no JDBC | email={} | accountId={} | message={}",
                            email,
                            accountId,
                            e.getMessage(),
                            e
                    );
                    throw e;

                } catch (Exception e) {
                    log.error(
                            "❌ Exceção inesperada no JDBC | email={} | accountId={} | type={} | message={}",
                            email,
                            accountId,
                            e.getClass().getName(),
                            e.getMessage(),
                            e
                    );
                    throw e;
                }
            });

            log.info("✅ ensureTenantIdentity concluído com sucesso | email={} | accountId={}", email, accountId);

        } catch (DataAccessException e) {
            log.error(
                    "❌ Falha de banco em ensureTenantIdentity | email={} | accountId={} | message={}",
                    email,
                    accountId,
                    e.getMessage(),
                    e
            );
            throw new ApiException(
                    ApiErrorCode.INTERNAL_ERROR,
                    "Falha interna ao garantir identidade de login."
            );

        } catch (ApiException e) {
            log.error(
                    "❌ ApiException em ensureTenantIdentity | email={} | accountId={} | code={} | message={}",
                    email,
                    accountId,
                    e.getError(),
                    e.getMessage(),
                    e
            );
            throw e;

        } catch (Exception e) {
            log.error(
                    "❌ Exceção inesperada em ensureTenantIdentity | email={} | accountId={} | type={} | message={}",
                    email,
                    accountId,
                    e.getClass().getName(),
                    e.getMessage(),
                    e
            );
            throw new ApiException(
                    ApiErrorCode.INTERNAL_ERROR,
                    "Erro interno ao garantir identidade de login."
            );
        }
    }

    /**
     * Garante identidade de login do control plane.
     *
     * @param email email do usuário do control plane
     * @param userId id do usuário
     */
    @Override
    public void ensureControlPlaneIdentity(String email, Long userId) {
        log.info("ensureControlPlaneIdentity chamado | email={} | userId={}", email, userId);
        // Implementar se necessário
    }

    /**
     * Remove identidade tenant do schema público.
     *
     * @param email email da identidade
     * @param accountId id da conta
     */
    @Override
    public void deleteTenantIdentity(String email, Long accountId) {
        log.debug("deleteTenantIdentity chamado | email={} | accountId={}", email, accountId);
        // Implementar se necessário
    }

    /**
     * Remove identidade do control plane por userId.
     *
     * @param userId id do usuário
     */
    @Override
    public void deleteControlPlaneIdentityByUserId(Long userId) {
        log.debug("deleteControlPlaneIdentityByUserId chamado | userId={}", userId);
        // Implementar se necessário
    }

    /**
     * Move identidade do control plane para novo email.
     *
     * @param userId id do usuário
     * @param newEmail novo email
     */
    @Override
    public void moveControlPlaneIdentity(Long userId, String newEmail) {
        log.debug("moveControlPlaneIdentity chamado | userId={} | newEmail={}", userId, newEmail);
        // Implementar se necessário
    }
}