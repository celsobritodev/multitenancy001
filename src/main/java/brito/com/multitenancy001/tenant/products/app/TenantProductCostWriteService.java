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
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        }

        if (costPrice == null) {
            throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "costPrice é obrigatório", 400);
        }

        if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "costPrice não pode ser negativo", 400);
        }

        log.info(
                "Atualizando costPrice de produto (TX). productId={}, costPrice={}",
                id,
                costPrice
        );

        Product product = tenantProductRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id,
                        404
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