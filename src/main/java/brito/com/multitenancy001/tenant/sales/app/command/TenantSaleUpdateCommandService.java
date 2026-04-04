package brito.com.multitenancy001.tenant.sales.app.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleUpdateRequest;
import brito.com.multitenancy001.tenant.sales.api.mapper.SaleApiMapper;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.domain.SaleItem;
import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;
import brito.com.multitenancy001.tenant.sales.persistence.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de atualização de venda.
 *
 * <p>Fluxo de inventory:</p>
 * <ol>
 *   <li>Carrega a venda atual</li>
 *   <li>Restaura estoque dos itens antigos ativos, quando necessário</li>
 *   <li>Atualiza payload da venda</li>
 *   <li>Reaplica consumo dos novos itens, quando necessário</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSaleUpdateCommandService {

    private final TenantSchemaUnitOfWork uow;
    private final SaleRepository saleRepository;
    private final SaleApiMapper mapper;
    private final AppClock appClock;
    private final TenantSaleMutationSupport mutationSupport;

    /**
     * Atualiza uma venda existente.
     *
     * @param accountId account atual
     * @param tenantSchema schema do tenant atual
     * @param saleId id da venda
     * @param req payload de atualização
     * @return venda atualizada
     */
    public SaleResponse update(Long accountId, String tenantSchema, UUID saleId, SaleUpdateRequest req) {
        if (saleId == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale id is required", 400);
        }
        if (req == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request is required", 400);
        }

        return uow.tx(tenantSchema, () -> {
            appClock.instant();

            SaleStatus resolvedStatus = mutationSupport.resolveRequiredStatus(req.status(), "update");

            log.info(
                    "SALE_UPDATE_START | accountId={} | tenantSchema={} | saleId={} | newCustomerId={} | newStatus={} | newSaleDate={} | newItemsCount={}",
                    accountId,
                    tenantSchema,
                    saleId,
                    req.customerId(),
                    resolvedStatus,
                    req.saleDate(),
                    req.items() != null ? req.items().size() : 0
            );

            mutationSupport.logRequestItems("SALE_UPDATE_REQUEST_ITEM", req.items());

            Sale sale = saleRepository.findByIdAndDeletedFalse(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            log.debug(
                    "SALE_UPDATE_LOADED | saleId={} | currentStatus={} | deleted={} | currentItemsCount={} | currentTotalAmount={} | currentAffectInventory={} | currentItems={}",
                    sale.getId(),
                    sale.getStatus(),
                    sale.isDeleted(),
                    sale.getItems() != null ? sale.getItems().size() : 0,
                    sale.getTotalAmount(),
                    mutationSupport.shouldAffectInventory(sale.getStatus()),
                    mutationSupport.describeItems(sale.getItems())
            );

            mutationSupport.restoreInventoryForCurrentActiveItems(sale);

            sale.setSaleDate(req.saleDate());
            sale.setStatus(resolvedStatus);

            mutationSupport.applyCustomerSnapshot(sale, req.customerId());

            if (sale.getItems() != null) {
                for (SaleItem old : sale.getItems()) {
                    if (old == null) {
                        continue;
                    }
                    old.softDelete();
                }
            }

            List<SaleItem> newItems = mutationSupport.buildItems(req.items(), sale);
            mutationSupport.validateItemsAgainstProducts(newItems);

            if (sale.getItems() == null) {
                sale.setItems(new ArrayList<>());
            }
            sale.getItems().addAll(newItems);

            sale.setTotalAmount(mutationSupport.sumItems(sale.getItems()));

            log.debug(
                    "SALE_UPDATE_PRE_PERSIST | saleId={} | newStatus={} | newTotalAmount={} | newAffectInventory={} | newItems={}",
                    sale.getId(),
                    sale.getStatus(),
                    sale.getTotalAmount(),
                    mutationSupport.shouldAffectInventory(sale.getStatus()),
                    mutationSupport.describeItems(newItems)
            );

            Sale saved = saleRepository.save(sale);

            log.debug(
                    "SALE_UPDATE_POST_PERSIST | saleId={} | totalAmount={} | status={} | activeItems={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getStatus(),
                    mutationSupport.describeActiveItems(saved.getItems())
            );

            mutationSupport.applyInventoryForSaleWrite(saved);

            log.info(
                    "SALE_UPDATE_SUCCESS | saleId={} | totalAmount={} | customerId={} | status={} | affectInventory={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getCustomerId(),
                    saved.getStatus(),
                    mutationSupport.shouldAffectInventory(saved.getStatus())
            );

            return mapper.toResponse(saved);
        });
    }
}