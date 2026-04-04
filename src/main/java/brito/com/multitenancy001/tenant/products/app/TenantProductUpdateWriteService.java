package brito.com.multitenancy001.tenant.products.app;

import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de atualização de produto no contexto tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar payload de update</li>
 *   <li>Carregar a entidade existente</li>
 *   <li>Aplicar mutações permitidas</li>
 *   <li>Resolver relações afetadas pelo update</li>
 *   <li>Persistir e reler a entidade final com relações carregadas</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductUpdateWriteService {

    private final TenantProductRepository tenantProductRepository;
    private final TenantProductWriteSupport support;

    /**
     * Atualiza produto existente.
     *
     * @param id id do produto
     * @param updateProductCommand payload de update
     * @return produto atualizado com relações carregadas
     */
    @TenantTx
    public Product update(UUID id, UpdateProductCommand updateProductCommand) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        }
        if (updateProductCommand == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

        log.info("Atualizando produto (TX). productId={}", id);

        support.validateUpdateCommand(updateProductCommand);

        Product existing = tenantProductRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id,
                        404
                ));

        support.applyUpdates(existing, updateProductCommand);
        tenantProductRepository.save(existing);

        log.info("Produto salvo após update. productId={}", id);

        Product loaded = support.loadWithRelationsOrThrow(id, "update");

        log.info(
                "Produto relido com relações após update. productId={}, sku={}",
                loaded.getId(),
                loaded.getSku()
        );

        return loaded;
    }
}