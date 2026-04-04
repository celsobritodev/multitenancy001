package brito.com.multitenancy001.tenant.products.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.tenant.products.api.dto.ProductResponse;
import brito.com.multitenancy001.tenant.products.api.mapper.ProductApiMapper;
import brito.com.multitenancy001.tenant.products.app.TenantProductService;
import brito.com.multitenancy001.tenant.products.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegate HTTP especializado em endpoints de leitura do módulo Products.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Consultas por id</li>
 *   <li>Listagens</li>
 *   <li>Buscas por categoria/subcategoria/fornecedor</li>
 *   <li>Consultas "any"</li>
 *   <li>Busca por filtros</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantProductQueryControllerDelegate {

    private final ProductApiMapper productApiMapper;
    private final TenantProductService tenantProductService;

    /**
     * Busca um produto por id no escopo do tenant atual.
     *
     * @param id id do produto
     * @return response com o produto encontrado
     */
    public ResponseEntity<ProductResponse> getById(UUID id) {
        log.info("Recebida requisição para buscar produto por id. productId={}", id);

        Product product = tenantProductService.findById(id);

        log.info("Produto carregado com sucesso. productId={}", id);
        return ResponseEntity.ok(productApiMapper.toResponse(product));
    }

    /**
     * Lista produtos paginados do tenant atual.
     *
     * @param pageable paginação
     * @return página de produtos
     */
    public ResponseEntity<Page<ProductResponse>> list(Pageable pageable) {
        log.info(
                "Recebida requisição para listar produtos paginados. pageNumber={}, pageSize={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize()
        );

        Page<ProductResponse> page = tenantProductService.findAll(pageable)
                .map(productApiMapper::toResponse);

        log.info(
                "Listagem paginada de produtos concluída. pageNumber={}, pageSize={}, returnedElements={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize(),
                page.getNumberOfElements()
        );

        return ResponseEntity.ok(page);
    }

    /**
     * Lista produtos por categoria.
     *
     * @param categoryId id da categoria
     * @return lista de produtos da categoria
     */
    public ResponseEntity<List<ProductResponse>> listByCategory(Long categoryId) {
        log.info("Recebida requisição para listar produtos por categoria. categoryId={}", categoryId);

        List<ProductResponse> out = tenantProductService.findByCategoryId(categoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Listagem por categoria concluída. categoryId={}, returnedElements={}", categoryId, out.size());
        return ResponseEntity.ok(out);
    }

    /**
     * Lista produtos por subcategoria.
     *
     * @param subcategoryId id da subcategoria
     * @return lista de produtos da subcategoria
     */
    public ResponseEntity<List<ProductResponse>> listBySubcategory(Long subcategoryId) {
        log.info("Recebida requisição para listar produtos por subcategoria. subcategoryId={}", subcategoryId);

        List<ProductResponse> out = tenantProductService.findBySubcategoryId(subcategoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Listagem por subcategoria concluída. subcategoryId={}, returnedElements={}", subcategoryId, out.size());
        return ResponseEntity.ok(out);
    }

    /**
     * Lista produtos por fornecedor.
     *
     * @param supplierId id do fornecedor
     * @return lista de produtos do fornecedor
     */
    public ResponseEntity<List<ProductResponse>> listBySupplier(UUID supplierId) {
        log.info("Recebida requisição para listar produtos por fornecedor. supplierId={}", supplierId);

        List<ProductResponse> out = tenantProductService.findBySupplierId(supplierId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Listagem por fornecedor concluída. supplierId={}, returnedElements={}", supplierId, out.size());
        return ResponseEntity.ok(out);
    }

    /**
     * Busca produtos de qualquer status por categoria.
     *
     * @param categoryId id da categoria
     * @return produtos encontrados
     */
    public ResponseEntity<List<ProductResponse>> findAnyByCategory(Long categoryId) {
        log.info("Recebida requisição para buscar produtos de qualquer status por categoria. categoryId={}", categoryId);

        List<ProductResponse> out = tenantProductService.findAnyByCategoryId(categoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Busca any/category concluída. categoryId={}, returnedElements={}", categoryId, out.size());
        return ResponseEntity.ok(out);
    }

    /**
     * Busca produtos de qualquer status por subcategoria.
     *
     * @param subcategoryId id da subcategoria
     * @return produtos encontrados
     */
    public ResponseEntity<List<ProductResponse>> findAnyBySubcategory(Long subcategoryId) {
        log.info(
                "Recebida requisição para buscar produtos de qualquer status por subcategoria. subcategoryId={}",
                subcategoryId
        );

        List<ProductResponse> out = tenantProductService.findAnyBySubcategoryId(subcategoryId).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Busca any/subcategory concluída. subcategoryId={}, returnedElements={}", subcategoryId, out.size());
        return ResponseEntity.ok(out);
    }

    /**
     * Busca produtos de qualquer status por marca.
     *
     * @param brand marca
     * @return produtos encontrados
     */
    public ResponseEntity<List<ProductResponse>> findAnyByBrand(String brand) {
        log.info("Recebida requisição para buscar produtos de qualquer status por marca. brand={}", brand);

        List<ProductResponse> out = tenantProductService.findAnyByBrandIgnoreCase(brand).stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Busca any/brand concluída. brand={}, returnedElements={}", brand, out.size());
        return ResponseEntity.ok(out);
    }

    /**
     * Busca produtos por filtros.
     *
     * @param name nome
     * @param minPrice preço mínimo
     * @param maxPrice preço máximo
     * @param minStock estoque mínimo
     * @param maxStock estoque máximo
     * @return lista filtrada
     */
    public ResponseEntity<List<ProductResponse>> searchByName(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minStock,
            Integer maxStock
    ) {
        log.info(
                "Recebida requisição de busca de produtos. name={}, minPrice={}, maxPrice={}, minStock={}, maxStock={}",
                name,
                minPrice,
                maxPrice,
                minStock,
                maxStock
        );

        List<ProductResponse> out = tenantProductService.searchProducts(name, minPrice, maxPrice, minStock, maxStock)
                .stream()
                .map(productApiMapper::toResponse)
                .toList();

        log.info("Busca de produtos concluída. returnedElements={}", out.size());
        return ResponseEntity.ok(out);
    }
}