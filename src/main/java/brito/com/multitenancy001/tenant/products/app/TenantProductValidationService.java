package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de validação do write path de produtos.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar integridade de criação.</li>
 *   <li>Validar command de update.</li>
 *   <li>Validar preço e regras numéricas básicas.</li>
 *   <li>Normalizar campos textuais de criação quando aplicável.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductValidationService {

    private final TenantProductRepository tenantProductRepository;

    /**
     * Valida integridade do produto antes da criação.
     *
     * @param product produto a validar
     */
    public void validateProductForCreate(Product product) {
        if (product == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

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

        String normalizedName = product.getName().trim();
        String normalizedSku = product.getSku().trim();

        if (normalizedName.isEmpty()) {
            throw new ApiException(ApiErrorCode.PRODUCT_NAME_REQUIRED, "name é obrigatório", 400);
        }

        if (normalizedSku.isEmpty()) {
            throw new ApiException(ApiErrorCode.SKU_REQUIRED, "sku é obrigatório", 400);
        }

        if (tenantProductRepository.existsBySkuAndDeletedFalse(normalizedSku)) {
            throw new ApiException(ApiErrorCode.SKU_ALREADY_EXISTS, "SKU já cadastrado: " + normalizedSku, 409);
        }

        product.setName(normalizedName);
        product.setSku(normalizedSku);

        if (product.getDescription() != null) {
            product.setDescription(product.getDescription().trim());
        }

        if (product.getBrand() != null) {
            String normalizedBrand = product.getBrand().trim();
            product.setBrand(normalizedBrand.isEmpty() ? null : normalizedBrand);
        }

        if (product.getBarcode() != null) {
            String normalizedBarcode = product.getBarcode().trim();
            product.setBarcode(normalizedBarcode.isEmpty() ? null : normalizedBarcode);
        }

        if (product.getDimensions() != null) {
            String normalizedDimensions = product.getDimensions().trim();
            product.setDimensions(normalizedDimensions.isEmpty() ? null : normalizedDimensions);
        }

        if (product.getStockQuantity() == null) {
            product.setStockQuantity(0);
        }
        if (product.getActive() == null) {
            product.setActive(true);
        }
        if (product.getDeleted() == null) {
            product.setDeleted(false);
        }

        log.debug(
                "PRODUCT_VALIDATE_CREATE_OK | sku={} | name={}",
                product.getSku(),
                product.getName()
        );
    }

    /**
     * Valida preço.
     *
     * @param price preço a validar
     */
    public void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new ApiException(ApiErrorCode.PRICE_REQUIRED, "price é obrigatório", 400);
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_PRICE, "price não pode ser negativo", 400);
        }
    }

    /**
     * Valida o command de update.
     *
     * @param updateProductCommand command de update
     */
    public void validateUpdateCommand(UpdateProductCommand updateProductCommand) {
        if (updateProductCommand == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

        if (updateProductCommand.clearSubcategory() && updateProductCommand.subcategoryId() != null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Nao pode informar subcategoryId e clearSubcategory=true ao mesmo tempo",
                    400
            );
        }

        if (updateProductCommand.sku() != null && updateProductCommand.sku().trim().isEmpty()) {
            throw new ApiException(ApiErrorCode.SKU_REQUIRED, "sku não pode ser vazio", 400);
        }

        if (updateProductCommand.name() != null && updateProductCommand.name().trim().isEmpty()) {
            throw new ApiException(ApiErrorCode.PRODUCT_NAME_REQUIRED, "name não pode ser vazio", 400);
        }

        if (updateProductCommand.price() != null) {
            validatePrice(updateProductCommand.price());
        }

        if (updateProductCommand.costPrice() != null
                && updateProductCommand.costPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "costPrice não pode ser negativo", 400);
        }

        if (updateProductCommand.stockQuantity() != null && updateProductCommand.stockQuantity() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "stockQuantity não pode ser negativo", 400);
        }

        if (updateProductCommand.minStock() != null && updateProductCommand.minStock() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "minStock não pode ser negativo", 400);
        }

        if (updateProductCommand.maxStock() != null && updateProductCommand.maxStock() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "maxStock não pode ser negativo", 400);
        }

        log.debug("PRODUCT_VALIDATE_UPDATE_COMMAND_OK");
    }
}