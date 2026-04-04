package brito.com.multitenancy001.tenant.sales.app.command;

import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.mapper.SaleApiMapper;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.persistence.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de restore de venda.
 *
 * <p>Regra:</p>
 * reativa a venda e reaplica consumo dos itens ativos
 * quando o status restaurado afeta inventory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSaleRestoreCommandService {

    private final TenantSchemaUnitOfWork uow;
    private final SaleRepository saleRepository;
    private final SaleApiMapper mapper;
    private final AppClock appClock;
    private final TenantSaleMutationHelper tenantSaleMutationHelper;

    /**
     * Restaura uma venda deletada logicamente.
     *
     * @param accountId account atual
     * @param tenantSchema schema do tenant atual
     * @param saleId id da venda
     * @return venda restaurada
     */
    public SaleResponse restore(Long accountId, String tenantSchema, UUID saleId) {
        if (saleId == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale id is required", 400);
        }

        return uow.tx(tenantSchema, () -> {
            appClock.instant();

            log.info(
                    "SALE_RESTORE_START | accountId={} | tenantSchema={} | saleId={}",
                    accountId,
                    tenantSchema,
                    saleId
            );

            Sale sale = saleRepository.findById(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            if (!sale.isDeleted()) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale is not deleted", 409);
            }

            log.debug(
                    "SALE_RESTORE_LOADED | saleId={} | status={} | deleted={} | affectInventory={} | items={}",
                    sale.getId(),
                    sale.getStatus(),
                    sale.isDeleted(),
                    tenantSaleMutationHelper.shouldAffectInventory(sale.getStatus()),
                    tenantSaleMutationHelper.describeItems(sale.getItems())
            );

            sale.restore();

            Sale saved = saleRepository.save(sale);

            log.debug(
                    "SALE_RESTORE_POST_PERSIST | saleId={} | status={} | deleted={} | activeItems={}",
                    saved.getId(),
                    saved.getStatus(),
                    saved.isDeleted(),
                    tenantSaleMutationHelper.describeActiveItems(saved.getItems())
            );

            tenantSaleMutationHelper.applyInventoryForSaleWrite(saved);

            log.info(
                    "SALE_RESTORE_SUCCESS | saleId={} | totalAmount={} | status={} | affectInventory={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getStatus(),
                    tenantSaleMutationHelper.shouldAffectInventory(saved.getStatus())
            );

            return mapper.toResponse(saved);
        });
    }
}