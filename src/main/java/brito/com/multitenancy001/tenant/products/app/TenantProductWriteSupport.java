package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina de suporte compartilhado para escrita de produtos.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar compatibilidade com os chamadores atuais.</li>
 *   <li>Delegar responsabilidades especializadas para componentes menores.</li>
 *   <li>Evitar que o write service dependa diretamente de múltiplos detalhes internos.</li>
 * </ul>
 *
 * <p>Esta classe não deve concentrar regra pesada.
 * Ela apenas centraliza a delegação para factory, validation, resolver e applier.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantProductWriteSupport {

    private final TenantProductFactory tenantProductFactory;
    private final TenantProductValidationService tenantProductValidationService;
    private final TenantProductRelationResolver tenantProductRelationResolver;
    private final TenantProductUpdateApplier tenantProductUpdateApplier;

    /**
     * Recarrega o produto com relações após persistência.
     *
     * @param productId id do produto
     * @param operation nome da operação para contexto de erro/log
     * @return produto com relações carregadas
     */
    public Product loadWithRelationsOrThrow(UUID productId, String operation) {
        log.debug(
                "PRODUCT_WRITE_SUPPORT_LOAD_WITH_RELATIONS | productId={} | operation={}",
                productId,
                operation
        );
        return tenantProductRelationResolver.loadWithRelationsOrThrow(productId, operation);
    }

    /**
     * Aplica alterações no produto existente.
     *
     * @param existing entidade existente
     * @param cmd command de update
     */
    public void applyUpdates(Product existing, UpdateProductCommand cmd) {
        log.debug(
                "PRODUCT_WRITE_SUPPORT_APPLY_UPDATES | productId={} | hasCommand={}",
                existing != null ? existing.getId() : null,
                cmd != null
        );
        tenantProductUpdateApplier.applyUpdates(existing, cmd);
    }

    /**
     * Resolve fornecedor completo do produto.
     *
     * @param product produto
     */
    public void resolveSupplier(Product product) {
        log.debug(
                "PRODUCT_WRITE_SUPPORT_RESOLVE_SUPPLIER | productId={} | supplierId={}",
                product != null ? product.getId() : null,
                product != null && product.getSupplier() != null ? product.getSupplier().getId() : null
        );
        tenantProductRelationResolver.resolveSupplier(product);
    }

    /**
     * Resolve categoria e subcategoria completas do produto.
     *
     * @param product produto
     */
    public void resolveCategoryAndSubcategory(Product product) {
        log.debug(
                "PRODUCT_WRITE_SUPPORT_RESOLVE_CATEGORY_SUBCATEGORY | productId={} | categoryId={} | subcategoryId={}",
                product != null ? product.getId() : null,
                product != null && product.getCategory() != null ? product.getCategory().getId() : null,
                product != null && product.getSubcategory() != null ? product.getSubcategory().getId() : null
        );
        tenantProductRelationResolver.resolveCategoryAndSubcategory(product);
    }

    /**
     * Valida se a subcategoria pertence à categoria informada.
     *
     * @param product produto
     */
    public void validateSubcategoryBelongsToCategory(Product product) {
        log.debug(
                "PRODUCT_WRITE_SUPPORT_VALIDATE_SUBCATEGORY_CATEGORY | productId={}",
                product != null ? product.getId() : null
        );
        tenantProductRelationResolver.validateSubcategoryBelongsToCategory(product);
    }

    /**
     * Valida integridade do produto antes da criação.
     *
     * @param product produto a validar
     */
    public void validateProductForCreate(Product product) {
        log.debug(
                "PRODUCT_WRITE_SUPPORT_VALIDATE_CREATE | productId={} | sku={}",
                product != null ? product.getId() : null,
                product != null ? product.getSku() : null
        );
        tenantProductValidationService.validateProductForCreate(product);
    }

    /**
     * Valida preço.
     *
     * @param price preço a validar
     */
    public void validatePrice(BigDecimal price) {
        tenantProductValidationService.validatePrice(price);
    }

    /**
     * Valida o command de update.
     *
     * @param updateProductCommand command de update
     */
    public void validateUpdateCommand(UpdateProductCommand updateProductCommand) {
        log.debug("PRODUCT_WRITE_SUPPORT_VALIDATE_UPDATE_COMMAND");
        tenantProductValidationService.validateUpdateCommand(updateProductCommand);
    }

    /**
     * Constrói entidade produto a partir do command de criação.
     *
     * @param createProductCommand command de criação
     * @return entidade de produto
     */
    public Product fromCreateCommand(CreateProductCommand createProductCommand) {
        log.debug(
                "PRODUCT_WRITE_SUPPORT_FROM_CREATE_COMMAND | accountId={} | sku={}",
                createProductCommand != null ? createProductCommand.accountId() : null,
                createProductCommand != null ? createProductCommand.sku() : null
        );
        return tenantProductFactory.fromCreateCommand(createProductCommand);
    }
}