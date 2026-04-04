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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Command/orchestration service do módulo de produtos no contexto tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar contexto mínimo do write path.</li>
 *   <li>Executar enforcement de quota antes da criação.</li>
 *   <li>Delegar a escrita efetiva para {@link TenantProductWriteService}.</li>
 * </ul>
 *
 * <p>Este bean não executa o save diretamente.
 * Ele orquestra o fluxo e delega a mutação transacional ao write service.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductCommandService {

    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;
    private final TenantProductWriteService tenantProductWriteService;

    /**
     * Cria um novo produto com validação prévia e enforcement de quota.
     *
     * <p>Fluxo:</p>
     * <ol>
     *   <li>Valida payload e contexto.</li>
     *   <li>Executa quota enforcement fora da transação tenant de escrita.</li>
     *   <li>Delega o save para {@link TenantProductWriteService}.</li>
     * </ol>
     *
     * @param createProductCommand payload de criação
     * @param tenantSchema schema do tenant
     * @return produto criado com relações carregadas
     */
    public Product create(CreateProductCommand createProductCommand, String tenantSchema) {
        if (createProductCommand == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

        if (createProductCommand.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        }

        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        String normalizedTenantSchema = tenantSchema.trim();

        if (!StringUtils.hasText(normalizedTenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        log.info(
                "Iniciando criação de produto (ORQUESTRAÇÃO). accountId={}, tenantSchema={}, sku={}, name={}",
                createProductCommand.accountId(),
                normalizedTenantSchema,
                createProductCommand.sku(),
                createProductCommand.name()
        );

        tenantQuotaEnforcementService.assertCanCreateProduct(
                createProductCommand.accountId(),
                normalizedTenantSchema
        );

        log.info(
                "Pre-check de quota concluído com sucesso para criação de produto. accountId={}, tenantSchema={}, sku={}",
                createProductCommand.accountId(),
                normalizedTenantSchema,
                createProductCommand.sku()
        );

        Product savedProduct = tenantProductWriteService.create(createProductCommand);

        log.info(
                "Criação de produto concluída com sucesso. accountId={}, tenantSchema={}, productId={}, sku={}",
                createProductCommand.accountId(),
                normalizedTenantSchema,
                savedProduct.getId(),
                savedProduct.getSku()
        );

        return savedProduct;
    }

    /**
     * Atualiza produto existente.
     *
     * @param id id do produto
     * @param updateProductCommand payload de update
     * @return produto atualizado
     */
    public Product update(UUID id, UpdateProductCommand updateProductCommand) {
        log.info("Delegando atualização de produto para write service. productId={}", id);
        return tenantProductWriteService.update(id, updateProductCommand);
    }

    /**
     * Alterna status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    public Product toggleActive(UUID id) {
        log.info("Delegando toggleActive de produto para write service. productId={}", id);
        return tenantProductWriteService.toggleActive(id);
    }

    /**
     * Atualiza o custo do produto.
     *
     * @param id id do produto
     * @param costPrice novo custo
     * @return produto atualizado
     */
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        log.info("Delegando updateCostPrice de produto para write service. productId={}, costPrice={}", id, costPrice);
        return tenantProductWriteService.updateCostPrice(id, costPrice);
    }
}