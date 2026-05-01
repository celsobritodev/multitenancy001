package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Query service do módulo de produtos no contexto tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Operações de leitura de catálogo.</li>
 *   <li>Consultas por id, categoria, subcategoria, fornecedor e marca.</li>
 *   <li>Busca paginada e busca por filtros.</li>
 * </ul>
 *
 * <p>Este bean concentra somente leituras de catálogo
 * e roda em transação read-only do tenant.</p>
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
public class TenantProductQueryService {

    private final TenantProductRepository tenantProductRepository;

    @TenantReadOnlyTx
    public Page<Product> findAll(Pageable pageable) {
        log.info(
                "Listando produtos paginados no tenant. pageNumber={}, pageSize={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize()
        );

        Page<Product> page = tenantProductRepository.findAll(pageable);

        log.info(
                "Listagem paginada concluída. pageNumber={}, pageSize={}, returnedElements={}",
                pageable == null ? null : pageable.getPageNumber(),
                pageable == null ? null : pageable.getPageSize(),
                page.getNumberOfElements()
        );

        return page;
    }

    @TenantReadOnlyTx
    public Product findById(UUID id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório");
        }

        log.info("Buscando produto por id no tenant. productId={}", id);

        Product product = tenantProductRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id
                ));

        log.info("Produto encontrado com sucesso. productId={}, sku={}", product.getId(), product.getSku());
        return product;
    }

    @TenantReadOnlyTx
    public List<Product> findByCategoryId(Long categoryId) {
        if (categoryId == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório");
        }

        log.info("Buscando produtos por categoria. categoryId={}", categoryId);
        List<Product> result = tenantProductRepository.findByCategoryId(categoryId);
        log.info("Busca por categoria concluída. categoryId={}, returnedElements={}", categoryId, result.size());
        return result;
    }

    @TenantReadOnlyTx
    public List<Product> findBySubcategoryId(Long subcategoryId) {
        if (subcategoryId == null) {
            throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "subcategoryId é obrigatório");
        }

        log.info("Buscando produtos por subcategoria. subcategoryId={}", subcategoryId);
        List<Product> result = tenantProductRepository.findBySubcategoryId(subcategoryId);
        log.info("Busca por subcategoria concluída. subcategoryId={}, returnedElements={}", subcategoryId, result.size());
        return result;
    }

    @TenantReadOnlyTx
    public List<Product> findBySupplierId(UUID supplierId) {
        if (supplierId == null) {
            throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "supplierId é obrigatório");
        }

        log.info("Buscando produtos por fornecedor. supplierId={}", supplierId);
        List<Product> result = tenantProductRepository.findBySupplierId(supplierId);
        log.info("Busca por fornecedor concluída. supplierId={}, returnedElements={}", supplierId, result.size());
        return result;
    }

    @TenantReadOnlyTx
    public List<Product> findAnyByCategoryId(Long categoryId) {
        if (categoryId == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório");
        }

        log.info("Buscando produtos any por categoria. categoryId={}", categoryId);
        List<Product> result = tenantProductRepository.findAnyByCategoryId(categoryId);
        log.info("Busca any por categoria concluída. categoryId={}, returnedElements={}", categoryId, result.size());
        return result;
    }

    @TenantReadOnlyTx
    public List<Product> findAnyBySubcategoryId(Long subcategoryId) {
        if (subcategoryId == null) {
            throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "subcategoryId é obrigatório");
        }

        log.info("Buscando produtos any por subcategoria. subcategoryId={}", subcategoryId);
        List<Product> result = tenantProductRepository.findAnyBySubcategoryId(subcategoryId);
        log.info("Busca any por subcategoria concluída. subcategoryId={}, returnedElements={}", subcategoryId, result.size());
        return result;
    }

    @TenantReadOnlyTx
    public List<Product> findAnyByBrandIgnoreCase(String brand) {
        if (!StringUtils.hasText(brand)) {
            throw new ApiException(ApiErrorCode.BRAND_REQUIRED, "brand é obrigatório");
        }

        String normalizedBrand = brand.trim();

        log.info("Buscando produtos any por marca. brand={}", normalizedBrand);
        List<Product> result = tenantProductRepository.findAnyByBrandIgnoreCase(normalizedBrand);
        log.info("Busca any por marca concluída. brand={}, returnedElements={}", normalizedBrand, result.size());
        return result;
    }

    @TenantReadOnlyTx
    public List<Product> searchProducts(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minStock,
            Integer maxStock
    ) {
        log.info(
                "Executando busca de produtos por filtros. name={}, minPrice={}, maxPrice={}, minStock={}, maxStock={}",
                name, minPrice, maxPrice, minStock, maxStock
        );

        return tenantProductRepository.searchProducts(
                name,
                minPrice,
                maxPrice,
                minStock,
                maxStock
        );
    }
}