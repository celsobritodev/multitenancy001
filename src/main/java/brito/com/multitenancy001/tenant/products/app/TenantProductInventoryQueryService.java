package brito.com.multitenancy001.tenant.products.app;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.tenant.products.app.dto.SupplierProductCountData;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Query service analítico do módulo de produtos no contexto tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Consultas agregadas por fornecedor.</li>
 *   <li>Cálculo de valor total de inventário.</li>
 *   <li>Contagem de produtos com estoque baixo.</li>
 * </ul>
 *
 * <p>Este bean concentra leituras analíticas e de inventory,
 * separado das leituras de catálogo.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProductInventoryQueryService {

    private final TenantProductRepository tenantProductRepository;

    /**
     * Conta produtos agrupados por fornecedor.
     *
     * @return lista com contagem por fornecedor
     */
    @TenantReadOnlyTx
    public List<SupplierProductCountData> countProductsBySupplier() {
        log.info("Contando produtos por fornecedor.");
        List<SupplierProductCountData> result = tenantProductRepository.countProductsBySupplier();
        log.info("Contagem por fornecedor concluída. returnedElements={}", result.size());
        return result;
    }

    /**
     * Calcula valor total do inventário.
     *
     * @return valor total do inventário
     */
    @TenantReadOnlyTx
    public BigDecimal calculateTotalInventoryValue() {
        log.info("Calculando valor total do inventário.");
        BigDecimal result = tenantProductRepository.calculateTotalInventoryValue();
        log.info("Valor total do inventário calculado. inventoryValue={}", result);
        return result;
    }

    /**
     * Conta produtos abaixo do estoque mínimo.
     *
     * @param threshold limite usado na consulta
     * @return quantidade de produtos
     */
    @TenantReadOnlyTx
    public Long countLowStockProducts(Integer threshold) {
        log.info("Contando produtos com estoque baixo. threshold={}", threshold);
        Long result = tenantProductRepository.countLowStockProducts(threshold);
        log.info("Contagem de low stock concluída. threshold={}, count={}", threshold, result);
        return result;
    }
}