package brito.com.multitenancy001.tenant.products.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.tenant.products.api.dto.ProductResponse;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpdateRequest;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpsertRequest;
import brito.com.multitenancy001.tenant.products.api.mapper.ProductApiMapper;
import brito.com.multitenancy001.tenant.products.app.TenantProductService;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegate HTTP especializado em endpoints de escrita do módulo Products.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Create detalhado</li>
 *   <li>Patch update</li>
 *   <li>Put update</li>
 *   <li>Toggle active</li>
 *   <li>Update cost price</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantProductCommandControllerDelegate {

    private final ProductApiMapper productApiMapper;
    private final TenantProductService tenantProductService;
    private final TenantProductControllerSupport support;

    /**
     * Alterna o status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    public ResponseEntity<ProductResponse> toggleActive(UUID id) {
        log.info("Recebida requisição para alternar status ativo do produto. productId={}", id);

        Product updated = tenantProductService.toggleActive(id);

        log.info(
                "Status ativo do produto alterado com sucesso via API. productId={}, active={}",
                updated.getId(),
                updated.getActive()
        );

        return ResponseEntity.ok(productApiMapper.toResponse(updated));
    }

    /**
     * Atualiza o custo do produto.
     *
     * @param id id do produto
     * @param costPrice novo custo
     * @return produto atualizado
     */
    public ResponseEntity<ProductResponse> updateCostPrice(UUID id, BigDecimal costPrice) {
        log.info("Recebida requisição para atualizar costPrice do produto. productId={}, costPrice={}", id, costPrice);

        Product updatedProduct = tenantProductService.updateCostPrice(id, costPrice);

        log.info(
                "CostPrice do produto atualizado com sucesso via API. productId={}, costPrice={}",
                updatedProduct.getId(),
                updatedProduct.getCostPrice()
        );

        return ResponseEntity.ok(productApiMapper.toResponse(updatedProduct));
    }

    /**
     * Cria produto detalhado no tenant atual.
     *
     * @param req payload HTTP de criação
     * @return produto criado
     */
    public ResponseEntity<ProductResponse> createDetailedProduct(ProductUpsertRequest req) {
        Long accountId = support.requireCurrentAccountId();
        String tenantSchema = support.requireCurrentTenantSchema();

        log.info(
                "Recebida requisição de criação detalhada de produto. accountId={}, tenantSchema={}, sku={}, name={}",
                accountId,
                tenantSchema,
                req.sku(),
                req.name()
        );

        support.validateCreateRequest(req);

        CreateProductCommand cmd = new CreateProductCommand(
                accountId,
                req.name(),
                req.description(),
                req.sku(),
                req.price(),
                req.stockQuantity(),
                req.minStock(),
                req.maxStock(),
                req.costPrice(),
                req.categoryId(),
                req.subcategoryId(),
                req.brand(),
                req.weightKg(),
                req.dimensions(),
                req.barcode(),
                req.active(),
                req.supplierId()
        );

        Product savedProduct = tenantProductService.create(cmd, tenantSchema);

        log.info(
                "Produto criado via API com sucesso. accountId={}, tenantSchema={}, productId={}, sku={}",
                accountId,
                tenantSchema,
                savedProduct.getId(),
                savedProduct.getSku()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(productApiMapper.toResponse(savedProduct));
    }

    /**
     * Executa atualização parcial de produto.
     *
     * @param id id do produto
     * @param req payload patch
     * @return produto atualizado
     */
    public ResponseEntity<ProductResponse> patchUpdate(UUID id, ProductUpdateRequest req) {
        log.info("Recebida requisição de patch de produto. productId={}", id);

        UpdateProductCommand cmd = support.buildUpdateCommandFrom(req);
        Product updated = tenantProductService.update(id, cmd);

        log.info("Patch de produto concluído com sucesso. productId={}", updated.getId());
        return ResponseEntity.ok(productApiMapper.toResponse(updated));
    }

    /**
     * Executa atualização completa do produto.
     *
     * @param id id do produto
     * @param req payload put
     * @return produto atualizado
     */
    public ResponseEntity<ProductResponse> putUpdate(UUID id, ProductUpsertRequest req) {
        log.info("Recebida requisição de put de produto. productId={}", id);

        support.validatePutRequest(req);

        UpdateProductCommand cmd = new UpdateProductCommand(
                req.name(),
                req.description(),
                req.sku(),
                req.price(),
                req.stockQuantity(),
                req.minStock(),
                req.maxStock(),
                req.costPrice(),
                req.categoryId(),
                req.subcategoryId(),
                Boolean.TRUE.equals(req.clearSubcategory()),
                req.brand(),
                req.weightKg(),
                req.dimensions(),
                req.barcode(),
                req.active(),
                req.supplierId()
        );

        Product updated = tenantProductService.update(id, cmd);

        log.info("Put de produto concluído com sucesso. productId={}", updated.getId());
        return ResponseEntity.ok(productApiMapper.toResponse(updated));
    }
}