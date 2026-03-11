package brito.com.multitenancy001.tenant.inventory.api;

import brito.com.multitenancy001.tenant.inventory.api.dto.InventoryAdjustRequest;
import brito.com.multitenancy001.tenant.inventory.api.dto.InventoryMovementResponse;
import brito.com.multitenancy001.tenant.inventory.api.dto.InventoryResponse;
import brito.com.multitenancy001.tenant.inventory.api.mapper.InventoryApiMapper;
import brito.com.multitenancy001.tenant.inventory.app.TenantInventoryService;
import brito.com.multitenancy001.tenant.inventory.app.command.AdjustInventoryCommand;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryItem;
import brito.com.multitenancy001.tenant.inventory.domain.InventoryMovement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller REST do módulo Inventory no contexto tenant.
 *
 * <p>Padrões aplicados:</p>
 * <ul>
 *   <li>Recebe e retorna apenas DTOs.</li>
 *   <li>Delega regra de negócio para Application Service.</li>
 *   <li>Utiliza RBAC via permissions do tenant.</li>
 *   <li>Logs operacionais detalhados.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/inventory")
@RequiredArgsConstructor
public class TenantInventoryController {

    private final TenantInventoryService inventoryService;

    /**
     * Consulta o saldo atual de inventory de um produto.
     *
     * @param productId id do produto
     * @return estado atual do estoque
     */
    @GetMapping("/products/{productId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")  // ✅ CORRETO
    public InventoryResponse getInventoryByProduct(@PathVariable UUID productId) {

        log.info("API inventory get by product start | productId={}", productId);

        InventoryItem item = inventoryService.getOrCreateInventory(productId);

        log.info(
                "API inventory get by product finished | productId={} | available={} | reserved={}",
                productId,
                item.getQuantityAvailable(),
                item.getQuantityReserved()
        );

        return InventoryApiMapper.toResponse(item);
    }

    /**
     * Lista o histórico de movimentações de estoque de um produto.
     *
     * @param productId id do produto
     * @return lista de movimentações
     */
    @GetMapping("/products/{productId}/movements")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_READ.asAuthority())")  // ✅ CORRETO
    public List<InventoryMovementResponse> listMovementsByProduct(@PathVariable UUID productId) {

        log.info("API inventory movement list start | productId={}", productId);

        List<InventoryMovement> movements = inventoryService.listMovementsByProduct(productId);

        log.info(
                "API inventory movement list finished | productId={} | totalMovements={}",
                productId,
                movements.size()
        );

        return InventoryApiMapper.toMovementResponses(movements);
    }

    /**
     * Realiza ajuste/movimentação de estoque.
     *
     * @param request request de ajuste
     * @return inventory atualizado
     */
    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_INVENTORY_WRITE.asAuthority())")
    public InventoryResponse adjustInventory(@Valid @RequestBody InventoryAdjustRequest request) {

        log.info(
                "API inventory adjustment start | productId={} | quantity={} | movementType={}",
                request.getProductId(),
                request.getQuantity(),
                request.getMovementType()
        );

        InventoryItem item = inventoryService.adjustInventory(
                AdjustInventoryCommand.builder()
                        .productId(request.getProductId())
                        .quantity(request.getQuantity())
                        .movementType(request.getMovementType())
                        .referenceType(request.getReferenceType())
                        .referenceId(request.getReferenceId())
                        .notes(request.getNotes())
                        .build()
        );

        log.info(
                "API inventory adjustment finished | productId={} | available={} | reserved={}",
                item.getProductId(),
                item.getQuantityAvailable(),
                item.getQuantityReserved()
        );

        return InventoryApiMapper.toResponse(item);
    }
}