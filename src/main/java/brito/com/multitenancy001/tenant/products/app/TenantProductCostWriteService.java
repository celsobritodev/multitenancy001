package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de atualização do custo do produto.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar entrada (id e costPrice)</li>
 *   <li>Aplicar regra de não negatividade</li>
 *   <li>Persistir alteração</li>
 *   <li>Recarregar produto com relações</li>
 * </ul>
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem status HTTP hardcoded</li>
 *   <li>Sem alteração de comportamento</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductCostWriteService {

    private final TenantProductRepository tenantProductRepository;
    private final TenantProductWriteHelper support;

    /**
     * Atualiza o custo do produto.
     *
     * @param id id do produto
     * @param costPrice novo custo
     * @return produto atualizado com relações carregadas
     */
    @TenantTx
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório");
        }

        if (costPrice == null) {
            throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "costPrice é obrigatório");
        }

        if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "costPrice não pode ser negativo");
        }

        log.info(
                "Atualizando costPrice de produto (TX). productId={}, costPrice={}",
                id,
                costPrice
        );

        Product product = tenantProductRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id
                ));

        product.updateCostPrice(costPrice);
        tenantProductRepository.save(product);

        log.info(
                "CostPrice do produto atualizado. productId={}, costPrice={}",
                id,
                costPrice
        );

        Product loaded = support.loadWithRelationsOrThrow(id, "updateCostPrice");

        log.info(
                "Produto relido com relações após updateCostPrice. productId={}, costPrice={}",
                loaded.getId(),
                loaded.getCostPrice()
        );

        return loaded;
    }
}