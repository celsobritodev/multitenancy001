package brito.com.multitenancy001.tenant.users.app.context;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantContextExecutor;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsService;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsSnapshot;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUsersListView;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serviço de aplicação responsável por consultas de usuários no contexto atual do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver contexto autenticado atual (accountId, tenantSchema, role).</li>
 *   <li>Executar consultas de usuários no schema tenant correto.</li>
 *   <li>Resolver snapshot de entitlements da conta quando o usuário atual for owner.</li>
 *   <li>Garantir separação rígida entre execução PUBLIC e execução TENANT.</li>
 * </ul>
 *
 * <p><b>Regra arquitetural crítica:</b></p>
 * <ul>
 *   <li>Qualquer acesso ao PUBLIC schema deve ocorrer sem TenantContext ativo.</li>
 *   <li>Por isso, este serviço força explicitamente escopo PUBLIC para resolver entitlements.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserCurrentContextQueryService {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantContextExecutor tenantExecutor;
    private final SecurityUtils securityUtils;
    private final AccountEntitlementsService accountEntitlementsService;

    /**
     * Lista os usuários do tenant atual.
     *
     * <p>Quando o usuário autenticado for owner do tenant, também inclui
     * snapshot de entitlements da conta.</p>
     *
     * @return visão agregada com usuários e, quando aplicável, entitlements da conta
     */
    public TenantUsersListView listTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();
        TenantRole currentRole = securityUtils.getCurrentTenantRole();
        boolean isOwner = currentRole != null && currentRole.isTenantOwner();

        log.info(
                "🔎 Iniciando listTenantUsers | accountId={} | tenantSchema={} | currentRole={} | isOwner={}",
                accountId,
                tenantSchema,
                currentRole,
                isOwner
        );

        AccountEntitlementsSnapshot entitlements = resolveEntitlementsInPublicContextIfOwner(accountId, tenantSchema, isOwner);

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            log.info(
                    "🏢 Listando usuários do tenant | accountId={} | tenantSchema={}",
                    accountId,
                    tenantSchema
            );

            List<TenantUser> users = tenantUserQueryService.listUsers(accountId);

            log.info(
                    "✅ Usuários do tenant listados com sucesso | accountId={} | tenantSchema={} | totalUsers={}",
                    accountId,
                    tenantSchema,
                    users.size()
            );

            return new TenantUsersListView(isOwner, entitlements, users);
        });
    }

    /**
     * Lista apenas os usuários habilitados do tenant atual.
     *
     * @return lista de usuários habilitados
     */
    public List<TenantUser> listEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        log.info(
                "🔎 Iniciando listEnabledTenantUsers | accountId={} | tenantSchema={}",
                accountId,
                tenantSchema
        );

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            log.info(
                    "🏢 Listando usuários habilitados | accountId={} | tenantSchema={}",
                    accountId,
                    tenantSchema
            );

            List<TenantUser> users = tenantUserQueryService.listEnabledUsers(accountId);

            log.info(
                    "✅ Usuários habilitados listados com sucesso | accountId={} | tenantSchema={} | totalEnabledUsers={}",
                    accountId,
                    tenantSchema,
                    users.size()
            );

            return users;
        });
    }

    /**
     * Busca um usuário do tenant atual pelo identificador.
     *
     * @param userId identificador do usuário
     * @return usuário encontrado
     */
    public TenantUser getTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        log.info(
                "🔎 Iniciando getTenantUser | userId={} | accountId={} | tenantSchema={}",
                userId,
                accountId,
                tenantSchema
        );

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            log.info(
                    "🏢 Buscando usuário no tenant | userId={} | accountId={} | tenantSchema={}",
                    userId,
                    accountId,
                    tenantSchema
            );

            TenantUser user = tenantUserQueryService.getUser(userId, accountId);

            log.info(
                    "✅ Usuário encontrado com sucesso | userId={} | accountId={} | tenantSchema={}",
                    userId,
                    accountId,
                    tenantSchema
            );

            return user;
        });
    }

    /**
     * Busca um usuário habilitado do tenant atual pelo identificador.
     *
     * @param userId identificador do usuário
     * @return usuário habilitado encontrado
     */
    public TenantUser getEnabledTenantUser(Long userId) {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        log.info(
                "🔎 Iniciando getEnabledTenantUser | userId={} | accountId={} | tenantSchema={}",
                userId,
                accountId,
                tenantSchema
        );

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            log.info(
                    "🏢 Buscando usuário habilitado no tenant | userId={} | accountId={} | tenantSchema={}",
                    userId,
                    accountId,
                    tenantSchema
            );

            TenantUser user = tenantUserQueryService.getEnabledUser(userId, accountId);

            log.info(
                    "✅ Usuário habilitado encontrado com sucesso | userId={} | accountId={} | tenantSchema={}",
                    userId,
                    accountId,
                    tenantSchema
            );

            return user;
        });
    }

    /**
     * Conta os usuários habilitados do tenant atual.
     *
     * @return total de usuários habilitados
     */
    public long countEnabledTenantUsers() {
        Long accountId = securityUtils.getCurrentAccountId();
        String tenantSchema = securityUtils.getCurrentTenantSchema();

        log.info(
                "🔎 Iniciando countEnabledTenantUsers | accountId={} | tenantSchema={}",
                accountId,
                tenantSchema
        );

        return tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            log.info(
                    "🏢 Contando usuários habilitados no tenant | accountId={} | tenantSchema={}",
                    accountId,
                    tenantSchema
            );

            long total = tenantUserQueryService.countEnabledUsersByAccount(accountId);

            log.info(
                    "✅ Contagem de usuários habilitados concluída | accountId={} | tenantSchema={} | totalEnabledUsers={}",
                    accountId,
                    tenantSchema,
                    total
            );

            return total;
        });
    }

    /**
     * Resolve entitlements da conta em escopo PUBLIC explícito quando o usuário atual for owner.
     *
     * <p>Motivação:</p>
     * <ul>
     *   <li>O {@link AccountEntitlementsService} executa em PUBLIC schema.</li>
     *   <li>O {@code PublicSchemaUnitOfWork} falha se houver {@code TenantContext} ativo.</li>
     *   <li>Por isso, forçamos escopo PUBLIC temporário com {@link TenantContext#scope(String)} usando {@code null}.</li>
     * </ul>
     *
     * @param accountId id da conta atual
     * @param tenantSchema schema atual do tenant
     * @param isOwner indica se o usuário atual é owner
     * @return snapshot de entitlements ou {@code null} quando não aplicável
     */
    private AccountEntitlementsSnapshot resolveEntitlementsInPublicContextIfOwner(
            Long accountId,
            String tenantSchema,
            boolean isOwner
    ) {
        if (!isOwner) {
            log.info(
                    "ℹ️ Usuário atual não é owner; entitlements não serão carregados | accountId={} | tenantSchema={}",
                    accountId,
                    tenantSchema
            );
            return null;
        }

        String previousTenant = TenantContext.getOrNull();

        log.info(
                "📋 Resolvendo entitlements em escopo PUBLIC explícito | accountId={} | tenantSchema={} | previousTenant={}",
                accountId,
                tenantSchema,
                previousTenant
        );

        try (TenantContext.Scope ignored = TenantContext.scope(null)) {
            AccountEntitlementsSnapshot snapshot =
                    accountEntitlementsService.resolveEffectiveByAccountId(accountId);

            log.info(
                    "✅ Entitlements resolvidos com sucesso em PUBLIC | accountId={} | snapshotPresente={}",
                    accountId,
                    snapshot != null
            );

            return snapshot;
        }
    }
}