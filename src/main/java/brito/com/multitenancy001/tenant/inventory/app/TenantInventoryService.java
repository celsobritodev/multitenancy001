package brito.com.multitenancy001.tenant.inventory.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.tenant.inventory.app.command.AdjustInventoryCommand;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryItem;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina do módulo de inventory no contexto tenant.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar compatibilidade com os chamadores atuais.</li>
 *   <li>Delegar query, adjustment e integração com sales para serviços especializados.</li>
 *   <li>Evitar concentração de responsabilidades em um único bean APP.</li>
 * </ul>
 *
 * <p>Esta classe não deve concentrar regra pesada.
 * Ela apenas mantém o contrato interno estável enquanto os casos de uso
 * ficam distribuídos em services semânticos.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantInventoryService {

    private final TenantInventoryQueryService tenantInventoryQueryService;
    private final TenantInventoryAdjustmentService tenantInventoryAdjustmentService;
    private final TenantInventorySalesIntegrationService tenantInventorySalesIntegrationService;

    /**
     * Retorna o estoque atual do produto, criando um registro zerado se necessário.
     *
     * @param productId id do produto
     * @return estado atual do estoque
     */
    public InventoryItem getOrCreateInventory(UUID productId) {
        log.debug("INVENTORY_SERVICE_FACADE_GET_OR_CREATE | productId={}", productId);
        return tenantInventoryQueryService.getOrCreateInventory(productId);
    }

    /**
     * Lista o histórico de movimentações do produto em ordem decrescente de criação.
     *
     * @param productId id do produto
     * @return lista de movimentações
     */
    public List<InventoryMovement> listMovementsByProduct(UUID productId) {
        log.debug("INVENTORY_SERVICE_FACADE_LIST_MOVEMENTS | productId={}", productId);
        return tenantInventoryQueryService.listMovementsByProduct(productId);
    }

    /**
     * Ajusta estoque com base em um comando manual ou sistêmico.
     *
     * @param command comando de ajuste
     * @return inventory atualizado
     */
    public InventoryItem adjustInventory(AdjustInventoryCommand command) {
        log.debug(
                "INVENTORY_SERVICE_FACADE_ADJUST | productId={} | movementType={}",
                command != null ? command.getProductId() : null,
                command != null ? command.getMovementType() : null
        );
        return tenantInventoryAdjustmentService.adjustInventory(command);
    }

    /**
     * Consome estoque por item de venda.
     *
     * @param saleId id da venda
     * @param productId id do produto
     * @param quantity quantidade positiva a ser consumida
     * @return inventory atualizado
     */
    public InventoryItem consumeStockForSale(UUID saleId, UUID productId, BigDecimal quantity) {
        log.debug(
                "INVENTORY_SERVICE_FACADE_CONSUME_FOR_SALE | saleId={} | productId={} | quantity={}",
                saleId,
                productId,
                quantity
        );
        return tenantInventorySalesIntegrationService.consumeStockForSale(saleId, productId, quantity);
    }

    /**
     * Devolve estoque anteriormente consumido por venda.
     *
     * @param saleId id da venda
     * @param productId id do produto
     * @param quantity quantidade positiva a devolver
     * @return inventory atualizado
     */
    public InventoryItem restoreStockFromSale(UUID saleId, UUID productId, BigDecimal quantity) {
        log.debug(
                "INVENTORY_SERVICE_FACADE_RESTORE_FROM_SALE | saleId={} | productId={} | quantity={}",
                saleId,
                productId,
                quantity
        );
        return tenantInventorySalesIntegrationService.restoreStockFromSale(saleId, productId, quantity);
    }
}