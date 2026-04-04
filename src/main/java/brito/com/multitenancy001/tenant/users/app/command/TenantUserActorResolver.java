package brito.com.multitenancy001.tenant.users.app.command;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Resolve o ator corrente para auditoria de usuários do tenant.
 */
@Component
@RequiredArgsConstructor
public class TenantUserActorResolver {

    private final SecurityUtils securityUtils;
    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantUserRepository tenantUserRepository;

    public TenantUserAuditService.Actor resolveActorOrNull(Long accountId, String tenantSchema) {
        try {
            Long actorUserId = securityUtils.getCurrentUserId();
            Long actorAccountId = securityUtils.getCurrentAccountId();

            if (actorUserId == null || actorAccountId == null) {
                return TenantUserAuditService.Actor.anonymous();
            }
            if (!actorAccountId.equals(accountId)) {
                return new TenantUserAuditService.Actor(actorUserId, null);
            }
            if (!StringUtils.hasText(tenantSchema)) {
                return new TenantUserAuditService.Actor(actorUserId, null);
            }

            String normalizedTenantSchema = tenantSchema.trim();

            String actorEmail = tenantSchemaUnitOfWork.readOnly(normalizedTenantSchema, () ->
                    tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(actorUserId, accountId)
                            .map(TenantUser::getEmail)
                            .orElse(null)
            );

            return new TenantUserAuditService.Actor(actorUserId, actorEmail);
        } catch (Exception ignored) {
            return TenantUserAuditService.Actor.anonymous();
        }
    }
}