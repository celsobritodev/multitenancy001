package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.tenant.products.app.command.CreateProductCommand;
import brito.com.multitenancy001.tenant.products.app.command.UpdateProductCommand;
import brito.com.multitenancy001.tenant.products.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina de escrita de produtos no contexto tenant.
 *
 * <p>Responsável apenas por preservar compatibilidade com os chamadores
 * e delegar os casos de uso de mutação para serviços especializados.</p>
 *
 * <p>Casos de uso delegados:</p>
 * <ul>
 *   <li>Criação de produto</li>
 *   <li>Atualização de produto</li>
 *   <li>Toggle de ativo/inativo</li>
 *   <li>Atualização de custo</li>
 * </ul>
 *
 * <p>Regras arquiteturais preservadas:</p>
 * <ul>
 *   <li>Controllers continuam finos</li>
 *   <li>Escrita permanece segregada em services semânticos</li>
 *   <li>Compatibilidade interna preservada</li>
 *   <li>{@code @TenantTx} continua aplicado nos beans concretos de escrita</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductWriteService {

    private final TenantProductCreateWriteService createWriteService;
    private final TenantProductUpdateWriteService updateWriteService;
    private final TenantProductActivationWriteService activationWriteService;
    private final TenantProductCostWriteService costWriteService;

    /**
     * Executa a criação efetiva do produto.
     *
     * @param createProductCommand payload de criação
     * @return produto criado com relações carregadas
     */
    public Product create(CreateProductCommand createProductCommand) {
        log.debug(
                "PRODUCT_WRITE_FACADE_CREATE | accountId={} | sku={}",
                createProductCommand != null ? createProductCommand.accountId() : null,
                createProductCommand != null ? createProductCommand.sku() : null
        );
        return createWriteService.create(createProductCommand);
    }

    /**
     * Atualiza produto existente.
     *
     * @param id id do produto
     * @param updateProductCommand payload de update
     * @return produto atualizado com relações carregadas
     */
    public Product update(UUID id, UpdateProductCommand updateProductCommand) {
        log.debug("PRODUCT_WRITE_FACADE_UPDATE | productId={}", id);
        return updateWriteService.update(id, updateProductCommand);
    }

    /**
     * Alterna o status ativo/inativo do produto.
     *
     * @param id id do produto
     * @return produto atualizado
     */
    public Product toggleActive(UUID id) {
        log.debug("PRODUCT_WRITE_FACADE_TOGGLE_ACTIVE | productId={}", id);
        return activationWriteService.toggleActive(id);
    }

    /**
     * Atualiza o custo do produto.
     *
     * @param id id do produto
     * @param costPrice novo custo
     * @return produto atualizado
     */
    public Product updateCostPrice(UUID id, BigDecimal costPrice) {
        log.debug(
                "PRODUCT_WRITE_FACADE_UPDATE_COST | productId={} | costPrice={}",
                id,
                costPrice
        );
        return costWriteService.updateCostPrice(id, costPrice);
    }
}