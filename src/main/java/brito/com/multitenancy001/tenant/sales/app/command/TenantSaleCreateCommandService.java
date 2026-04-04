package brito.com.multitenancy001.tenant.sales.app.command;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleCreateRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.mapper.SaleApiMapper;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.domain.SaleItem;
import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;
import brito.com.multitenancy001.tenant.sales.persistence.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de criação de venda.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar request de criação</li>
 *   <li>Resolver status obrigatório</li>
 *   <li>Aplicar snapshot de customer</li>
 *   <li>Construir e validar itens</li>
 *   <li>Persistir a venda</li>
 *   <li>Aplicar integração de estoque quando necessário</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSaleCreateCommandService {

    private final TenantSchemaUnitOfWork uow;
    private final SaleRepository saleRepository;
    private final SaleApiMapper mapper;
    private final AppClock appClock;
    private final TenantSaleMutationHelper tenanantSaleMutationHelper;

    /**
     * Cria uma nova venda.
     *
     * @param accountId account atual
     * @param tenantSchema schema do tenant atual
     * @param req payload de criação
     * @return venda criada
     */
    public SaleResponse create(Long accountId, String tenantSchema, SaleCreateRequest req) {
        if (req == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request is required", 400);
        }

        return uow.tx(tenantSchema, () -> {
            appClock.instant();

            SaleStatus resolvedStatus = tenanantSaleMutationHelper.resolveRequiredStatus(req.status(), "create");

            log.info(
                    "SALE_CREATE_START | accountId={} | tenantSchema={} | customerId={} | status={} | saleDate={} | itemsCount={}",
                    accountId,
                    tenantSchema,
                    req.customerId(),
                    resolvedStatus,
                    req.saleDate(),
                    req.items() != null ? req.items().size() : 0
            );

            tenanantSaleMutationHelper.logRequestItems("SALE_CREATE_REQUEST_ITEM", req.items());

            Sale sale = new Sale();
            sale.setSaleDate(req.saleDate());
            sale.setStatus(resolvedStatus);

            tenanantSaleMutationHelper.applyCustomerSnapshot(sale, req.customerId());

            List<SaleItem> items = tenanantSaleMutationHelper.buildItems(req.items(), sale);
            tenanantSaleMutationHelper.validateItemsAgainstProducts(items);

            sale.setItems(items);
            sale.setTotalAmount(tenanantSaleMutationHelper.sumItems(items));

            log.debug(
                    "SALE_CREATE_PRE_PERSIST | tenantSchema={} | customerId={} | totalAmount={} | affectInventory={} | items={}",
                    tenantSchema,
                    sale.getCustomerId(),
                    sale.getTotalAmount(),
                    tenanantSaleMutationHelper.shouldAffectInventory(sale.getStatus()),
                    tenanantSaleMutationHelper.describeItems(sale.getItems())
            );

            Sale saved = saleRepository.save(sale);

            log.debug(
                    "SALE_CREATE_POST_PERSIST | saleId={} | itemsCount={} | totalAmount={} | status={}",
                    saved.getId(),
                    saved.getItems() != null ? saved.getItems().size() : 0,
                    saved.getTotalAmount(),
                    saved.getStatus()
            );

            tenanantSaleMutationHelper.applyInventoryForSaleWrite(saved);

            log.info(
                    "SALE_CREATE_SUCCESS | saleId={} | totalAmount={} | customerId={} | status={} | affectInventory={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getCustomerId(),
                    saved.getStatus(),
                    tenanantSaleMutationHelper.shouldAffectInventory(saved.getStatus())
            );

            return mapper.toResponse(saved);
        });
    }
}