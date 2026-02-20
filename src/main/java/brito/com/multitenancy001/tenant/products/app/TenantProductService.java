package brito.com.multitenancy001.tenant.products.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.categories.domain.Category;
import brito.com.multitenancy001.tenant.categories.domain.Subcategory;
import brito.com.multitenancy001.tenant.categories.persistence.TenantCategoryRepository;
import brito.com.multitenancy001.tenant.categories.persistence.TenantSubcategoryRepository;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.app.dto.SupplierProductCountData;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import brito.com.multitenancy001.tenant.suppliers.persistence.TenantSupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application Service (Tenant): Products.
 *
 * Nota importante (regra definitiva):
 * - A API mapeia Product -> DTO fora da transação (sem OpenSessionInView).
 * - Portanto, métodos de READ usados pela API precisam retornar entidades com relações inicializadas
 *   (category/subcategory/supplier), evitando LazyInitializationException.
 *
 * Estratégia:
 * - Repository expõe "read-safe" (EntityGraph / fetch join).
 * - Writes (create/update/toggle/cost) fazem save e retornam "re-fetch read-safe" por ID.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;

    private final TenantProductRepository tenantProductRepository;
    private final TenantSupplierRepository tenantSupplierRepository;
    private final TenantCategoryRepository tenantCategoryRepository;
    private final TenantSubcategoryRepository tenantSubcategoryRepository;

    /**
     * Lista paginada (read-safe: relações carregadas via EntityGraph no repository).
     */
    @TenantReadOnlyTx
    public Page<Product> findAll(Pageable pageable) {
        // método: delega paginação ao repository (read-safe)
        return tenantProductRepository.findAll(pageable);
    }

    /**
     * Busca por id (read-safe: relações carregadas).
     */
    @TenantReadOnlyTx
    public Product findById(UUID id) {
        // método: valida input e busca com relações inicializadas
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);

        return tenantProductRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id, 404));
    }

    /**
     * Cria um Product via Command.
     *
     * Importante:
     * - Controller não cria entity
     * - Service monta o Product e resolve relacionamentos
     * - Retorno é read-safe (re-fetch com relações) para o mapper não quebrar fora da transação.
     */
    @TenantTx
    public Product create(CreateProductCommand cmd) {
        // método: valida command, monta entity, resolve relacionamentos e persiste
        if (cmd == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);

        Product product = fromCreateCommand(cmd);
        validateProductForCreate(product);

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            resolveCategoryAndSubcategory(product);
            resolveSupplier(product);
            validateSubcategoryBelongsToCategory(product);

            Product saved = tenantProductRepository.save(product);

            // ✅ retorno read-safe (evita LazyInitialization no mapper)
            return tenantProductRepository.findWithRelationsById(saved.getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado após criação (ID: " + saved.getId() + ")", 500));
        });
    }

    /**
     * Update via Command.
     *
     * Retorno é read-safe (re-fetch).
     */
    @TenantTx
    public Product update(UUID id, UpdateProductCommand cmd) {
        // método: valida input, aplica alterações e persiste
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);
        if (cmd == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Product existing = tenantProductRepository.findById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado com ID: " + id, 404));

            if (StringUtils.hasText(cmd.name())) existing.setName(cmd.name().trim());
            if (cmd.description() != null) existing.setDescription(cmd.description());

            if (StringUtils.hasText(cmd.sku())) {
                String sku = cmd.sku().trim();
                Optional<Product> withSku = tenantProductRepository.findBySku(sku);
                if (withSku.isPresent() && !withSku.get().getId().equals(id)) {
                    throw new ApiException(ApiErrorCode.SKU_ALREADY_EXISTS, "SKU já cadastrado: " + sku, 409);
                }
                existing.setSku(sku);
            }

            if (cmd.price() != null) {
                validatePrice(cmd.price());
                existing.updatePrice(cmd.price());
            }

            if (cmd.stockQuantity() != null) existing.setStockQuantity(cmd.stockQuantity());
            if (cmd.minStock() != null) existing.setMinStock(cmd.minStock());
            if (cmd.maxStock() != null) existing.setMaxStock(cmd.maxStock());

            if (cmd.costPrice() != null) existing.updateCostPrice(cmd.costPrice());
            if (cmd.brand() != null) existing.setBrand(cmd.brand());
            if (cmd.weightKg() != null) existing.setWeightKg(cmd.weightKg());
            if (cmd.dimensions() != null) existing.setDimensions(cmd.dimensions());
            if (cmd.barcode() != null) existing.setBarcode(cmd.barcode());
            if (cmd.active() != null) existing.setActive(cmd.active());

            if (cmd.categoryId() != null) {
                Category category = tenantCategoryRepository.findById(cmd.categoryId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada", 404));
                existing.setCategory(category);
            }

            if (cmd.clearSubcategory()) {
                existing.setSubcategory(null);
            } else if (cmd.subcategoryId() != null) {
                Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(cmd.subcategoryId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada", 404));
                existing.setSubcategory(sub);
            }

            if (cmd.supplierId() != null) {
                Supplier supplier = tenantSupplierRepository.findById(cmd.supplierId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado", 404));
                existing.setSupplier(supplier);
            }

            validateSubcategoryBelongsToCategory(existing);

            tenantProductRepository.save(existing);

            // ✅ retorno read-safe
            return tenantProductRepository.findWithRelationsById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado após update (ID: " + id + ")", 500));
        });
    }

    /**
     * Alterna status ativo/inativo do produto.
     *
     * ✅ EXISTE para o controller compilar
     * ✅ Retorna read-safe para o mapper não estourar lazy.
     */
    @TenantTx
    public Product toggleActive(UUID id) {
        // método: alterna flag active e persiste
        if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);

        String tenantSchema = requireBoundTenantSchema();

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Product product = tenantProductRepository.findById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado com ID: " + id, 404));

            product.setActive(!Boolean.TRUE.equals(product.getActive()));
            tenantProductRepository.save(product);

            return tenantProductRepository.findWithRelationsById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                            "Produto não encontrado após toggleActive (ID: " + id + ")", 500));
        });
    }

   /**
 * Atualiza o custo do produto (costPrice).
 *
 * ✅ EXISTE para o controller compilar
 * ✅ Retorna read-safe para o mapper não estourar lazy.
 */
@TenantTx
public Product updateCostPrice(UUID id, BigDecimal costPrice) {
    // método: valida input e persiste
    if (id == null) throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "id é obrigatório", 400);

    // ✅ você NÃO tem COST_PRICE_REQUIRED no enum -> reutiliza PRICE_REQUIRED
    if (costPrice == null) {
        throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "costPrice é obrigatório", 400);
    }

    // ✅ você NÃO tem INVALID_COST_PRICE no enum -> reutiliza INVALID_AMOUNT (genérico)
    if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
        throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "costPrice não pode ser negativo", 400);
    }

    String tenantSchema = requireBoundTenantSchema();

    return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
        Product product = tenantProductRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado com ID: " + id, 404));

        product.updateCostPrice(costPrice);
        tenantProductRepository.save(product);

        return tenantProductRepository.findWithRelationsById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND,
                        "Produto não encontrado após updateCostPrice (ID: " + id + ")", 500));
    });
}

    // =========================
    // READ endpoints da API
    // =========================

    @TenantReadOnlyTx
    public List<SupplierProductCountData> countProductsBySupplier() {
        // método: mapeia resultado cru do repository para DTO de APP
        List<Object[]> rows = tenantProductRepository.countProductsBySupplier();
        return rows.stream()
                .map(row -> new SupplierProductCountData((UUID) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    @TenantReadOnlyTx
    public List<Product> findByCategoryId(Long categoryId) {
        // método: valida input e consulta read-safe no repository
        if (categoryId == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório", 400);
        return tenantProductRepository.findByCategoryId(categoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findBySubcategoryId(Long subcategoryId) {
        // método: valida input e consulta read-safe no repository
        if (subcategoryId == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "subcategoryId é obrigatório", 400);
        return tenantProductRepository.findBySubcategoryId(subcategoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findBySupplierId(UUID supplierId) {
        // método: valida input e consulta read-safe no repository
        if (supplierId == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "supplierId é obrigatório", 400);
        return tenantProductRepository.findBySupplierId(supplierId);
    }

    @TenantReadOnlyTx
    public List<Product> findAnyByCategoryId(Long categoryId) {
        // método: valida input e consulta read-safe
        if (categoryId == null) throw new ApiException(ApiErrorCode.CATEGORY_ID_REQUIRED, "categoryId é obrigatório", 400);
        return tenantProductRepository.findAnyByCategoryId(categoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findAnyBySubcategoryId(Long subcategoryId) {
        // método: valida input e consulta read-safe
        if (subcategoryId == null) throw new ApiException(ApiErrorCode.SUBCATEGORY_ID_REQUIRED, "subcategoryId é obrigatório", 400);
        return tenantProductRepository.findAnyBySubcategoryId(subcategoryId);
    }

    @TenantReadOnlyTx
    public List<Product> findAnyByBrandIgnoreCase(String brand) {
        // método: valida input e consulta read-safe
        if (!StringUtils.hasText(brand)) throw new ApiException(ApiErrorCode.BRAND_REQUIRED, "brand é obrigatório", 400);
        return tenantProductRepository.findAnyByBrandIgnoreCase(brand.trim());
    }

    @TenantReadOnlyTx
    public BigDecimal calculateTotalInventoryValue() {
        // método: delega cálculo ao repository
        return tenantProductRepository.calculateTotalInventoryValue();
    }

    @TenantReadOnlyTx
    public Long countLowStockProducts(Integer threshold) {
        // método: delega cálculo ao repository
        return tenantProductRepository.countLowStockProducts(threshold);
    }

    @TenantReadOnlyTx
    public List<Product> searchProducts(String name, BigDecimal minPrice, BigDecimal maxPrice, Integer minStock, Integer maxStock) {
        // método: delega filtro ao repository (read-safe)
        return tenantProductRepository.searchProducts(name, minPrice, maxPrice, minStock, maxStock);
    }

    // =========================================================
    // Helpers (resolvers/validators)
    // =========================================================

    private void resolveSupplier(Product product) {
        // método: resolve supplier por id (se informado)
        if (product.getSupplier() != null && product.getSupplier().getId() != null) {
            UUID supplierId = product.getSupplier().getId();
            Supplier supplier = tenantSupplierRepository.findById(supplierId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND,
                            "Fornecedor não encontrado com ID: " + supplierId, 404));
            product.setSupplier(supplier);
        }
    }

    private void resolveCategoryAndSubcategory(Product product) {
        // método: resolve category obrigatória e subcategory opcional
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória", 400);
        }

        Category category = tenantCategoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.CATEGORY_NOT_FOUND, "Categoria não encontrada", 404));
        product.setCategory(category);

        if (product.getSubcategory() != null && product.getSubcategory().getId() != null) {
            Subcategory sub = tenantSubcategoryRepository.findByIdWithCategory(product.getSubcategory().getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SUBCATEGORY_NOT_FOUND, "Subcategoria não encontrada", 404));
            product.setSubcategory(sub);
        } else {
            product.setSubcategory(null);
        }
    }

    private void validateSubcategoryBelongsToCategory(Product product) {
        // método: valida consistência category/subcategory
        if (product.getSubcategory() == null) return;

        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.CATEGORY_REQUIRED, "Categoria é obrigatória", 400);
        }

        if (product.getSubcategory().getCategory() == null || product.getSubcategory().getCategory().getId() == null) {
            throw new ApiException(ApiErrorCode.INVALID_SUBCATEGORY,
                    "Subcategoria sem categoria associada (cadastro inconsistente)", 409);
        }

        Long subCatCategoryId = product.getSubcategory().getCategory().getId();
        Long productCategoryId = product.getCategory().getId();

        if (!subCatCategoryId.equals(productCategoryId)) {
            throw new ApiException(ApiErrorCode.INVALID_SUBCATEGORY, "Subcategoria não pertence à categoria informada", 409);
        }
    }

    private void validateProductForCreate(Product product) {
        // método: validações mínimas para create
        if (product == null) throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);

        if (!StringUtils.hasText(product.getName())) {
            throw new ApiException(ApiErrorCode.PRODUCT_NAME_REQUIRED, "name é obrigatório", 400);
        }

        if (!StringUtils.hasText(product.getSku())) {
            throw new ApiException(ApiErrorCode.SKU_REQUIRED, "sku é obrigatório", 400);
        }

        if (product.getPrice() == null) {
            throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "price é obrigatório", 400);
        }

        validatePrice(product.getPrice());

        // SKU único
        String sku = product.getSku().trim();
        Optional<Product> existing = tenantProductRepository.findBySku(sku);
        if (existing.isPresent()) {
            throw new ApiException(ApiErrorCode.SKU_ALREADY_EXISTS, "SKU já cadastrado: " + sku, 409);
        }

        // normalizações
        product.setName(product.getName().trim());
        product.setSku(sku);

        if (product.getStockQuantity() == null) product.setStockQuantity(0);
        if (product.getActive() == null) product.setActive(true);
        if (product.getDeleted() == null) product.setDeleted(false);
    }

    private void validatePrice(BigDecimal price) {
        // método: valida preço não-negativo
        if (price == null) throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "price é obrigatório", 400);
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_PRICE, "price não pode ser negativo", 400);
        }
    }

    private String requireBoundTenantSchema() {
        // método: garante TenantContext bindado
        String tenantSchema = TenantContext.getOrNull();
        if (tenantSchema == null) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED,
                    "TenantContext não está bindado (tenantSchema=null). Operação requer contexto TENANT.", 500);
        }
        return tenantSchema;
    }

    private Product fromCreateCommand(CreateProductCommand cmd) {
        // método: monta entity Product a partir do command, sem resolver relacionamentos
        Product product = new Product();
        product.setName(cmd.name());
        product.setDescription(cmd.description());
        product.setSku(cmd.sku());
        product.setPrice(cmd.price());
        product.setStockQuantity(cmd.stockQuantity());
        product.setMinStock(cmd.minStock());
        product.setMaxStock(cmd.maxStock());
        product.setCostPrice(cmd.costPrice());
        product.setBrand(cmd.brand());
        product.setWeightKg(cmd.weightKg());
        product.setDimensions(cmd.dimensions());
        product.setBarcode(cmd.barcode());
        product.setActive(cmd.active());

        // category é obrigatória (resolvida depois)
        Category category = new Category();
        category.setId(cmd.categoryId());
        product.setCategory(category);

        // subcategory opcional
        if (cmd.subcategoryId() != null) {
            Subcategory sub = new Subcategory();
            sub.setId(cmd.subcategoryId());
            product.setSubcategory(sub);
        } else {
            product.setSubcategory(null);
        }

        // supplier opcional
        if (cmd.supplierId() != null) {
            Supplier supplier = new Supplier();
            supplier.setId(cmd.supplierId());
            product.setSupplier(supplier);
        } else {
            product.setSupplier(null);
        }

        // defaults explícitos
        product.setDeleted(false);
        if (product.getStockQuantity() == null) product.setStockQuantity(0);
        if (product.getActive() == null) product.setActive(true);

        return product;
    }
}