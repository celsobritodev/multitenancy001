package brito.com.multitenancy001.tenant.products.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de criação efetiva de produto no contexto tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar payload de criação</li>
 *   <li>Construir entidade inicial</li>
 *   <li>Validar coerência de domínio</li>
 *   <li>Resolver relações obrigatórias/opcionais</li>
 *   <li>Persistir e reler a entidade final com relações carregadas</li>
 * </ul>
 *
 * <p>Quota enforcement não deve ocorrer aqui.
 * Esse passo deve permanecer fora, no service de orquestração.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductCreateWriteService {

    private final TenantProductRepository tenantProductRepository;
    private final TenantProductWriteSupport support;

    /**
     * Executa a criação efetiva do produto dentro de transação tenant.
     *
     * @param createProductCommand payload de criação
     * @return produto criado com relações carregadas
     */
    @TenantTx
    public Product create(CreateProductCommand createProductCommand) {
        if (createProductCommand == null) {
            throw new ApiException(ApiErrorCode.PRODUCT_REQUIRED, "payload é obrigatório", 400);
        }

        log.info(
                "Iniciando criação de produto (TX). accountId={}, sku={}, name={}",
                createProductCommand.accountId(),
                createProductCommand.sku(),
                createProductCommand.name()
        );

        Product product = support.fromCreateCommand(createProductCommand);
        support.validateProductForCreate(product);

        support.resolveCategoryAndSubcategory(product);
        support.resolveSupplier(product);
        support.validateSubcategoryBelongsToCategory(product);

        Product saved = tenantProductRepository.save(product);

        log.info(
                "Produto salvo no tenant com sucesso. accountId={}, productId={}, sku={}",
                createProductCommand.accountId(),
                saved.getId(),
                saved.getSku()
        );

        Product loaded = support.loadWithRelationsOrThrow(saved.getId(), "criação");

        log.info(
                "Produto relido com relações após criação. productId={}, sku={}",
                loaded.getId(),
                loaded.getSku()
        );

        return loaded;
    }
}