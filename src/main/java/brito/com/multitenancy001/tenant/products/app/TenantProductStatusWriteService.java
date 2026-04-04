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
 * Caso de uso de mutação de status de produto.
 *
 * <p>Responsabilidade atual:</p>
 * <ul>
 *   <li>Alternar ativo/inativo</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductStatusWriteService {

    private final TenantProductRepository tenantProductRepository;
    private final TenantProductWriteHelper tenantProductWriteHelper;

    /**
     * Alterna o status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    @TenantTx
    public Product toggleActive(UUID id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        }

        log.info("PRODUCT_TOGGLE_ACTIVE_START | productId={}", id);

        Product product = tenantProductRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id,
                        404
                ));

        product.setActive(!Boolean.TRUE.equals(product.getActive()));
        tenantProductRepository.save(product);

        log.info(
                "PRODUCT_TOGGLE_ACTIVE_SAVED | productId={} | active={}",
                id,
                product.getActive()
        );

        Product loaded = tenantProductWriteHelper.loadWithRelationsOrThrow(
                id,
                "Produto não encontrado após toggleActive (ID: " + id + ")"
        );

        log.info(
                "PRODUCT_TOGGLE_ACTIVE_SUCCESS | productId={} | active={}",
                loaded.getId(),
                loaded.getActive()
        );

        return loaded;
    }
}