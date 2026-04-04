package brito.com.multitenancy001.tenant.products.app;

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
 * Caso de uso de alternância de status ativo/inativo do produto.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductActivationWriteService {

    private final TenantProductRepository tenantProductRepository;
    private final TenantProductWriteSupport support;

    /**
     * Alterna o status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado com relações carregadas
     */
    @TenantTx
    public Product toggleActive(UUID id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        }

        log.info("Alternando status ativo de produto (TX). productId={}", id);

        Product product = tenantProductRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id,
                        404
                ));

        product.setActive(!Boolean.TRUE.equals(product.getActive()));
        tenantProductRepository.save(product);

        log.info(
                "Status ativo do produto alterado. productId={}, active={}",
                id,
                product.getActive()
        );

        Product loaded = support.loadWithRelationsOrThrow(id, "toggleActive");

        log.info(
                "Produto relido com relações após toggleActive. productId={}, active={}",
                loaded.getId(),
                loaded.getActive()
        );

        return loaded;
    }
}