package brito.com.multitenancy001.tenant.products.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.tenant.products.api.dto.SupplierProductCountResponse;
import brito.com.multitenancy001.tenant.products.app.TenantProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegate HTTP especializado em endpoints analíticos e de inventory
 * do módulo Products.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantProductInventoryControllerDelegate {

    private final TenantProductService tenantProductService;

    /**
     * Retorna contagem agregada de produtos por fornecedor.
     *
     * @return lista agregada por fornecedor
     */
    public ResponseEntity<List<SupplierProductCountResponse>> countBySupplier() {
        log.info("Recebida requisição para contagem de produtos por fornecedor.");

        List<SupplierProductCountResponse> out = tenantProductService.countProductsBySupplier().stream()
                .map(row -> new SupplierProductCountResponse(row.supplierId(), row.productCount()))
                .toList();

        log.info("Contagem por fornecedor concluída. returnedElements={}", out.size());
        return ResponseEntity.ok(out);
    }

    /**
     * Retorna o valor total do inventário do tenant.
     *
     * @return valor total do inventário
     */
    public ResponseEntity<BigDecimal> getTotalInventoryValue() {
        log.info("Recebida requisição para consultar valor total do inventário.");

        BigDecimal value = tenantProductService.calculateTotalInventoryValue();
        BigDecimal normalizedValue = value != null ? value : BigDecimal.ZERO;

        log.info("Valor total do inventário calculado com sucesso. inventoryValue={}", normalizedValue);
        return ResponseEntity.ok(normalizedValue);
    }

    /**
     * Retorna a quantidade de produtos com estoque baixo.
     *
     * @param threshold limiar de estoque baixo
     * @return quantidade encontrada
     */
    public ResponseEntity<Long> countLowStock(Integer threshold) {
        log.info("Recebida requisição para contar produtos com estoque baixo. threshold={}", threshold);

        Long count = tenantProductService.countLowStockProducts(threshold);
        Long normalizedCount = count != null ? count : 0L;

        log.info("Contagem de low stock concluída com sucesso. threshold={}, count={}", threshold, normalizedCount);
        return ResponseEntity.ok(normalizedCount);
    }
}