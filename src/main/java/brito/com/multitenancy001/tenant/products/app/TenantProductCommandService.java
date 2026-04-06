package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.subscription.app.TenantQuotaEnforcementService;
import brito.com.multitenancy001.tenant.subscription.app.TenantUsageSnapshotAfterCommitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductCommandService {

    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;
    private final TenantProductWriteService tenantProductWriteService;
    private final TenantUsageSnapshotAfterCommitService tenantUsageSnapshotAfterCommitService;

    public Product create(CreateProductCommand cmd, String tenantSchema) {
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }
        if (cmd.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        }
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        String normalizedTenantSchema = tenantSchema.trim();

        log.info("CREATE PRODUCT START | accountId={} tenantSchema={} sku={}",
                cmd.accountId(), normalizedTenantSchema, cmd.sku());

        tenantQuotaEnforcementService.assertCanCreateProduct(
                cmd.accountId(),
                normalizedTenantSchema
        );

        Product saved = tenantProductWriteService.create(cmd);

        // 🔥 NOVO: refresh snapshot
        tenantUsageSnapshotAfterCommitService.scheduleRefreshAfterCommit(
                cmd.accountId(),
                normalizedTenantSchema
        );

        log.info("CREATE PRODUCT DONE | productId={}", saved.getId());

        return saved;
    }

    public Product update(UUID id, UpdateProductCommand cmd) {
        return tenantProductWriteService.update(id, cmd);
    }

    public Product toggleActive(UUID id) {
        return tenantProductWriteService.toggleActive(id);
    }

    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        return tenantProductWriteService.updateCostPrice(id, costPrice);
    }
}