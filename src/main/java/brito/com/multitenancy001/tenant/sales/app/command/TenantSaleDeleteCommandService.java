package brito.com.multitenancy001.tenant.sales.app.command;

import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.persistence.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de delete lógico de venda.
 *
 * <p>Regra:</p>
 * restaura estoque dos itens ativos antes do soft delete,
 * desde que o status atual da venda afete inventory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSaleDeleteCommandService {

    private final TenantSchemaUnitOfWork uow;
    private final SaleRepository saleRepository;
    private final AppClock appClock;
    private final TenantSaleMutationHelper tenantSaleMutationHelper;

    /**
     * Deleta logicamente uma venda.
     *
     * @param accountId account atual
     * @param tenantSchema schema do tenant atual
     * @param saleId id da venda
     */
    public void delete(Long accountId, String tenantSchema, UUID saleId) {
        if (saleId == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale id is required", 400);
        }

        uow.tx(tenantSchema, () -> {
            appClock.instant();

            log.info(
                    "SALE_DELETE_START | accountId={} | tenantSchema={} | saleId={}",
                    accountId,
                    tenantSchema,
                    saleId
            );

            Sale sale = saleRepository.findByIdAndDeletedFalse(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            log.debug(
                    "SALE_DELETE_LOADED | saleId={} | status={} | affectInventory={} | itemsCount={} | items={}",
                    sale.getId(),
                    sale.getStatus(),
                    tenantSaleMutationHelper.shouldAffectInventory(sale.getStatus()),
                    sale.getItems() != null ? sale.getItems().size() : 0,
                    tenantSaleMutationHelper.describeItems(sale.getItems())
            );

            tenantSaleMutationHelper.restoreInventoryForCurrentActiveItems(sale);

            sale.softDelete();
            saleRepository.save(sale);

            log.info("SALE_DELETE_SUCCESS | saleId={}", saleId);
            return null;
        });
    }
}