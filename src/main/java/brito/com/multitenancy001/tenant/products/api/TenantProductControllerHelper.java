package brito.com.multitenancy001.tenant.products.api;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpdateRequest;
import brito.com.multitenancy001.tenant.products.api.dto.ProductUpsertRequest;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper compartilhado do boundary HTTP de Products.
 *
 * <p>Centraliza helpers reutilizados pelos delegates:</p>
 * <ul>
 *   <li>montagem de {@link UpdateProductCommand}</li>
 *   <li>guards HTTP simples</li>
 *   <li>resolução de accountId</li>
 *   <li>resolução de tenantSchema</li>
 * </ul>
 *
 * <p>Regras de domínio mais pesadas continuam pertencendo à camada APP.</p>
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem status HTTP hardcoded.</li>
 *   <li>Sem alteração de comportamento.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantProductControllerHelper {

    private final TenantRequestIdentityService tenantRequestIdentityService;

    /**
     * Monta o command de update a partir do request de patch.
     *
     * @param req request HTTP
     * @return command de update
     */
    public UpdateProductCommand buildUpdateCommandFrom(ProductUpdateRequest req) {
        boolean clearSubcategory = Boolean.TRUE.equals(req.clearSubcategory());

        if (req.subcategoryId() != null) {
            clearSubcategory = false;
        }

        return new UpdateProductCommand(
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
                clearSubcategory,
                req.brand(),
                req.weightKg(),
                req.dimensions(),
                req.barcode(),
                req.active(),
                req.supplierId()
        );
    }

    /**
     * Valida regras mínimas do payload de criação no boundary HTTP.
     *
     * <p>As validações de domínio continuam pertencendo à camada APP/write service.
     * Aqui ficam apenas guards simples para evitar request semanticamente inconsistente.</p>
     *
     * @param req payload de criação
     */
    public void validateCreateRequest(ProductUpsertRequest req) {
        if (req == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório");
        }

        if (Boolean.TRUE.equals(req.clearSubcategory()) && req.subcategoryId() != null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Nao pode informar subcategoryId e clearSubcategory=true ao mesmo tempo"
            );
        }
    }

    /**
     * Valida guards simples do PUT no boundary HTTP.
     *
     * @param req payload de put
     */
    public void validatePutRequest(ProductUpsertRequest req) {
        if (Boolean.TRUE.equals(req.clearSubcategory()) && req.subcategoryId() != null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SUBCATEGORY,
                    "Nao pode informar subcategoryId e clearSubcategory=true ao mesmo tempo"
            );
        }
    }

    /**
     * Resolve o accountId da identidade tenant autenticada.
     *
     * @return accountId atual
     */
    public Long requireCurrentAccountId() {
        Long accountId = tenantRequestIdentityService.getCurrentAccountId();

        if (accountId == null) {
            throw new ApiException(
                    ApiErrorCode.ACCOUNT_REQUIRED,
                    "Não foi possível resolver a conta do tenant autenticado"
            );
        }

        log.debug("PRODUCT_CONTROLLER_ACCOUNT_RESOLVED | accountId={}", accountId);
        return accountId;
    }

    /**
     * Resolve o tenantSchema atual do contexto.
     *
     * @return tenantSchema atual
     */
    public String requireCurrentTenantSchema() {
        String tenantSchema = TenantContext.getOrNull();

        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(
                    ApiErrorCode.TENANT_CONTEXT_REQUIRED,
                    "Não foi possível resolver o tenantSchema do contexto atual"
            );
        }

        String normalizedTenantSchema = tenantSchema.trim();
        log.debug("PRODUCT_CONTROLLER_TENANT_SCHEMA_RESOLVED | tenantSchema={}", normalizedTenantSchema);
        return normalizedTenantSchema;
    }
}