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

/**
 * Serviço de comando para operações de escrita de produtos no contexto Tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar entradas mínimas do fluxo de criação.</li>
 *   <li>Aplicar enforcement de quota antes da criação efetiva.</li>
 *   <li>Delegar persistência e mutações ao write service especializado.</li>
 *   <li>Agendar refresh de snapshot de uso após commit.</li>
 * </ul>
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem status HTTP hardcoded.</li>
 *   <li>Logs e fluxo preservados.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductCommandService {

    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;
    private final TenantProductWriteService tenantProductWriteService;
    private final TenantUsageSnapshotAfterCommitService tenantUsageSnapshotAfterCommitService;

    /**
     * Cria produto no tenant respeitando quota do plano.
     *
     * @param cmd comando de criação
     * @param tenantSchema schema tenant atual
     * @return produto criado
     */
    public Product create(CreateProductCommand cmd, String tenantSchema) {
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório");
        }
        if (cmd.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório");
        }
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório");
        }

        String normalizedTenantSchema = tenantSchema.trim();

        log.info("CREATE PRODUCT START | accountId={} tenantSchema={} sku={}",
                cmd.accountId(), normalizedTenantSchema, cmd.sku());

        tenantQuotaEnforcementService.assertCanCreateProduct(
                cmd.accountId(),
                normalizedTenantSchema
        );

        Product saved = tenantProductWriteService.create(cmd);

        tenantUsageSnapshotAfterCommitService.scheduleRefreshAfterCommit(
                cmd.accountId(),
                normalizedTenantSchema
        );

        log.info("CREATE PRODUCT DONE | productId={}", saved.getId());

        return saved;
    }

    /**
     * Atualiza produto existente.
     *
     * @param id id do produto
     * @param cmd comando de atualização
     * @return produto atualizado
     */
    public Product update(UUID id, UpdateProductCommand cmd) {
        return tenantProductWriteService.update(id, cmd);
    }

    /**
     * Alterna status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    public Product toggleActive(UUID id) {
        return tenantProductWriteService.toggleActive(id);
    }

    /**
     * Atualiza preço de custo do produto.
     *
     * @param id id do produto
     * @param costPrice novo preço de custo
     * @return produto atualizado
     */
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        return tenantProductWriteService.updateCostPrice(id, costPrice);
    }
}