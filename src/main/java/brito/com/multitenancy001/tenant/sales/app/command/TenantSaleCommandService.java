package brito.com.multitenancy001.tenant.sales.app.command;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.customers.domain.Customer;
import brito.com.multitenancy001.tenant.customers.persistence.TenantCustomerRepository;
import brito.com.multitenancy001.tenant.inventory.app.TenantInventoryService;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleCreateRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleItemRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleUpdateRequest;
import brito.com.multitenancy001.tenant.sales.api.mapper.SaleApiMapper;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.domain.SaleItem;
import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;
import brito.com.multitenancy001.tenant.sales.persistence.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application Service de command do módulo Sales.
 *
 * <p>Responsabilidades principais:</p>
 * <ul>
 *   <li>Criar vendas.</li>
 *   <li>Atualizar vendas existentes.</li>
 *   <li>Executar delete lógico e restore.</li>
 *   <li>Aplicar snapshot do customer dentro da venda.</li>
 *   <li>Construir e validar itens da venda.</li>
 *   <li>Integrar o fluxo transacional da venda com o módulo de estoque.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>Toda mutação roda via {@link TenantSchemaUnitOfWork}.</li>
 *   <li>{@link AppClock} é a única fonte de tempo da aplicação.</li>
 *   <li>Controllers não acessam repositories diretamente.</li>
 *   <li>Entidades relacionadas são validadas antes de persistência.</li>
 * </ul>
 *
 * <p>Regras de integração com Inventory:</p>
 * <ul>
 *   <li>{@code OPEN}, {@code CONFIRMED} e {@code PAID} afetam estoque.</li>
 *   <li>{@code DRAFT} e {@code CANCELLED} não afetam estoque.</li>
 *   <li>No update, o estoque anterior é restaurado antes da reaplicação dos novos itens.</li>
 *   <li>No delete, o estoque é restaurado.</li>
 *   <li>No restore, o consumo é reaplicado quando o status afeta inventory.</li>
 * </ul>
 *
 * <p>Observação para troubleshooting:</p>
 * <ul>
 *   <li>Esta classe possui logs reforçados para rastrear claramente:
 *     create/update/delete/restore, status efetivo da venda, itens ativos,
 *     e decisão de impacto ou não no inventory.</li>
 *   <li>Status nulo não é aceito. Isso evita sucesso silencioso com venda
 *     persistida sem impacto de estoque por ausência de status.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSaleCommandService {

    private final TenantSchemaUnitOfWork uow;
    private final SaleRepository saleRepository;
    private final TenantCustomerRepository tenantCustomerRepository;
    private final TenantProductRepository tenantProductRepository;
    private final TenantInventoryService tenantInventoryService;
    private final SaleApiMapper mapper;
    private final AppClock appClock;

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

            SaleStatus resolvedStatus = resolveRequiredStatus(req.status(), "create");

            log.info(
                    "SALE_CREATE_START | accountId={} | tenantSchema={} | customerId={} | status={} | saleDate={} | itemsCount={}",
                    accountId,
                    tenantSchema,
                    req.customerId(),
                    resolvedStatus,
                    req.saleDate(),
                    req.items() != null ? req.items().size() : 0
            );

            logRequestItems("SALE_CREATE_REQUEST_ITEM", req.items());

            Sale sale = new Sale();
            sale.setSaleDate(req.saleDate());
            sale.setStatus(resolvedStatus);

            applyCustomerSnapshot(sale, req.customerId());

            List<SaleItem> items = buildItems(req.items(), sale);
            validateItemsAgainstProducts(items);

            sale.setItems(items);
            sale.setTotalAmount(sumItems(items));

            log.debug(
                    "SALE_CREATE_PRE_PERSIST | tenantSchema={} | customerId={} | totalAmount={} | affectInventory={} | items={}",
                    tenantSchema,
                    sale.getCustomerId(),
                    sale.getTotalAmount(),
                    shouldAffectInventory(sale.getStatus()),
                    describeItems(sale.getItems())
            );

            Sale saved = saleRepository.save(sale);

            log.debug(
                    "SALE_CREATE_POST_PERSIST | saleId={} | itemsCount={} | totalAmount={} | status={}",
                    saved.getId(),
                    saved.getItems() != null ? saved.getItems().size() : 0,
                    saved.getTotalAmount(),
                    saved.getStatus()
            );

            applyInventoryForSaleWrite(saved);

            log.info(
                    "SALE_CREATE_SUCCESS | saleId={} | totalAmount={} | customerId={} | status={} | affectInventory={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getCustomerId(),
                    saved.getStatus(),
                    shouldAffectInventory(saved.getStatus())
            );

            return mapper.toResponse(saved);
        });
    }

    /**
     * Atualiza uma venda existente.
     *
     * <p>Fluxo de inventory:</p>
     * <ol>
     *   <li>Carrega a venda atual.</li>
     *   <li>Restaura estoque dos itens antigos ativos, se o status antigo afetava estoque.</li>
     *   <li>Substitui payload da venda.</li>
     *   <li>Reaplica consumo dos novos itens, se o novo status afetar estoque.</li>
     * </ol>
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

            SaleStatus resolvedStatus = resolveRequiredStatus(req.status(), "update");

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

            logRequestItems("SALE_UPDATE_REQUEST_ITEM", req.items());

            Sale sale = saleRepository.findByIdAndDeletedFalse(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            log.debug(
                    "SALE_UPDATE_LOADED | saleId={} | currentStatus={} | deleted={} | currentItemsCount={} | currentTotalAmount={} | currentAffectInventory={} | currentItems={}",
                    sale.getId(),
                    sale.getStatus(),
                    sale.isDeleted(),
                    sale.getItems() != null ? sale.getItems().size() : 0,
                    sale.getTotalAmount(),
                    shouldAffectInventory(sale.getStatus()),
                    describeItems(sale.getItems())
            );

            restoreInventoryForCurrentActiveItems(sale);

            sale.setSaleDate(req.saleDate());
            sale.setStatus(resolvedStatus);

            applyCustomerSnapshot(sale, req.customerId());

            if (sale.getItems() != null) {
                for (SaleItem old : sale.getItems()) {
                    if (old == null) {
                        continue;
                    }
                    old.softDelete();
                }
            }

            List<SaleItem> newItems = buildItems(req.items(), sale);
            validateItemsAgainstProducts(newItems);

            if (sale.getItems() == null) {
                sale.setItems(new ArrayList<>());
            }
            sale.getItems().addAll(newItems);

            sale.setTotalAmount(sumItems(sale.getItems()));

            log.debug(
                    "SALE_UPDATE_PRE_PERSIST | saleId={} | newStatus={} | newTotalAmount={} | newAffectInventory={} | newItems={}",
                    sale.getId(),
                    sale.getStatus(),
                    sale.getTotalAmount(),
                    shouldAffectInventory(sale.getStatus()),
                    describeItems(newItems)
            );

            Sale saved = saleRepository.save(sale);

            log.debug(
                    "SALE_UPDATE_POST_PERSIST | saleId={} | totalAmount={} | status={} | activeItems={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getStatus(),
                    describeActiveItems(saved.getItems())
            );

            applyInventoryForSaleWrite(saved);

            log.info(
                    "SALE_UPDATE_SUCCESS | saleId={} | totalAmount={} | customerId={} | status={} | affectInventory={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getCustomerId(),
                    saved.getStatus(),
                    shouldAffectInventory(saved.getStatus())
            );

            return mapper.toResponse(saved);
        });
    }

    /**
     * Deleta logicamente uma venda.
     *
     * <p>Regra:</p>
     * restaura estoque dos itens ativos antes do soft delete,
     * desde que o status atual da venda afete inventory.
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
                    shouldAffectInventory(sale.getStatus()),
                    sale.getItems() != null ? sale.getItems().size() : 0,
                    describeItems(sale.getItems())
            );

            restoreInventoryForCurrentActiveItems(sale);

            sale.softDelete();
            saleRepository.save(sale);

            log.info("SALE_DELETE_SUCCESS | saleId={}", saleId);
            return null;
        });
    }

    /**
     * Restaura uma venda deletada logicamente.
     *
     * <p>Regra:</p>
     * reativa a venda e reaplica consumo dos itens ativos
     * quando o status restaurado afeta inventory.
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
                    shouldAffectInventory(sale.getStatus()),
                    describeItems(sale.getItems())
            );

            sale.restore();

            Sale saved = saleRepository.save(sale);

            log.debug(
                    "SALE_RESTORE_POST_PERSIST | saleId={} | status={} | deleted={} | activeItems={}",
                    saved.getId(),
                    saved.getStatus(),
                    saved.isDeleted(),
                    describeActiveItems(saved.getItems())
            );

            applyInventoryForSaleWrite(saved);

            log.info(
                    "SALE_RESTORE_SUCCESS | saleId={} | totalAmount={} | status={} | affectInventory={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getStatus(),
                    shouldAffectInventory(saved.getStatus())
            );

            return mapper.toResponse(saved);
        });
    }

    /**
     * Preenche snapshot de customer na venda.
     *
     * <p>Quando o customerId é nulo, os campos de snapshot são limpos.</p>
     *
     * @param sale venda alvo
     * @param customerId customer opcional
     */
    private void applyCustomerSnapshot(Sale sale, UUID customerId) {
        if (sale == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale is required", 400);
        }

        if (customerId == null) {
            log.debug("SALE_CUSTOMER_SNAPSHOT_CLEAR | reason=no_customer");

            sale.setCustomerId(null);
            sale.setCustomerName(null);
            sale.setCustomerDocument(null);
            sale.setCustomerEmail(null);
            sale.setCustomerPhone(null);
            return;
        }

        Customer customer = tenantCustomerRepository.findById(customerId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CUSTOMER_NOT_FOUND, "customer not found", 404));

        if (customer.isDeleted()) {
            throw new ApiException(ApiErrorCode.CUSTOMER_NOT_FOUND, "customer deleted", 404);
        }

        sale.setCustomerId(customer.getId());
        sale.setCustomerName(trimToNull(customer.getName()));
        sale.setCustomerDocument(trimToNull(customer.getDocument()));
        sale.setCustomerEmail(trimToNull(customer.getEmail()));
        sale.setCustomerPhone(trimToNull(customer.getPhone()));

        log.debug(
                "SALE_CUSTOMER_SNAPSHOT_APPLIED | customerId={} | customerName={} | customerDocument={} | customerEmail={}",
                customer.getId(),
                customer.getName(),
                customer.getDocument(),
                customer.getEmail()
        );
    }

    /**
     * Constrói os itens da venda e garante o vínculo obrigatório com a venda pai.
     *
     * @param reqItems itens recebidos no request
     * @param sale venda pai
     * @return lista pronta para persistência
     */
    private static List<SaleItem> buildItems(List<SaleItemRequest> reqItems, Sale sale) {
        if (reqItems == null || reqItems.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "items is required", 400);
        }
        if (sale == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale is required to build items", 400);
        }

        List<SaleItem> out = new ArrayList<>();

        for (SaleItemRequest r : reqItems) {
            if (r == null) {
                continue;
            }

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setProductId(r.productId());
            saleItem.setProductName(r.productName());
            saleItem.setQuantity(r.quantity());
            saleItem.setUnitPrice(r.unitPrice());
            saleItem.recalcTotal();
            saleItem.setDeleted(false);

            out.add(saleItem);
        }

        if (out.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "items is required", 400);
        }

        return out;
    }

    /**
     * Valida existência dos produtos e os campos mínimos de cada item.
     *
     * @param items itens da venda
     */
    private void validateItemsAgainstProducts(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "items is required", 400);
        }

        for (SaleItem item : items) {
            if (item == null) {
                continue;
            }

            log.debug(
                    "SALE_ITEM_VALIDATE | productId={} | quantity={} | unitPrice={} | totalPrice={}",
                    item.getProductId(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotalPrice()
            );

            if (item.getProductId() == null) {
                throw new ApiException(ApiErrorCode.PRODUCT_ID_REQUIRED, "productId is required", 400);
            }
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "item quantity must be positive", 400);
            }
            if (!tenantProductRepository.existsById(item.getProductId())) {
                throw new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "product not found", 404);
            }
        }
    }

    /**
     * Aplica consumo de inventory para todos os itens ativos da venda,
     * quando o status da venda afeta estoque.
     *
     * @param sale venda salva
     */
    private void applyInventoryForSaleWrite(Sale sale) {
        if (sale == null || sale.getItems() == null || sale.getItems().isEmpty()) {
            log.debug("SALE_INVENTORY_WRITE_SKIP | reason=sale_or_items_empty");
            return;
        }

        boolean affectInventory = shouldAffectInventory(sale.getStatus());

        log.info(
                "SALE_INVENTORY_WRITE_DECISION | saleId={} | status={} | shouldAffectInventory={} | activeItems={}",
                sale.getId(),
                sale.getStatus(),
                affectInventory,
                describeActiveItems(sale.getItems())
        );

        if (!affectInventory) {
            log.info(
                    "SALE_INVENTORY_WRITE_SKIP | saleId={} | status={} | reason=status_does_not_affect_inventory",
                    sale.getId(),
                    sale.getStatus()
            );
            return;
        }

        log.info(
            "SALE_INVENTORY_WRITE_START | saleId={} | status={} | itemsCount={} | activeItems={}",
            sale.getId(),
            sale.getStatus(),
            sale.getItems().size(),
            describeActiveItems(sale.getItems())
        );

        for (SaleItem item : sale.getItems()) {
            if (item == null || item.isDeleted()) {
                continue;
            }

            log.debug(
                    "SALE_INVENTORY_WRITE_ITEM | saleId={} | productId={} | quantity={} | deleted={}",
                    sale.getId(),
                    item.getProductId(),
                    item.getQuantity(),
                    item.isDeleted()
            );

            tenantInventoryService.consumeStockForSale(
                    sale.getId(),
                    item.getProductId(),
                    item.getQuantity()
            );
        }

        log.info(
                "SALE_INVENTORY_WRITE_FINISH | saleId={} | status={}",
                sale.getId(),
                sale.getStatus()
        );
    }

    /**
     * Restaura inventory dos itens ativos atuais da venda,
     * quando o status atual afeta estoque.
     *
     * @param sale venda
     */
    private void restoreInventoryForCurrentActiveItems(Sale sale) {
        if (sale == null || sale.getItems() == null || sale.getItems().isEmpty()) {
            log.debug("SALE_INVENTORY_RESTORE_SKIP | reason=sale_or_items_empty");
            return;
        }

        boolean affectInventory = shouldAffectInventory(sale.getStatus());

        log.info(
                "SALE_INVENTORY_RESTORE_DECISION | saleId={} | status={} | shouldAffectInventory={} | activeItems={}",
                sale.getId(),
                sale.getStatus(),
                affectInventory,
                describeActiveItems(sale.getItems())
        );

        if (!affectInventory) {
            log.info(
                    "SALE_INVENTORY_RESTORE_SKIP | saleId={} | status={} | reason=status_does_not_affect_inventory",
                    sale.getId(),
                    sale.getStatus()
            );
            return;
        }

        log.info(
                "SALE_INVENTORY_RESTORE_START | saleId={} | status={} | itemsCount={} | activeItems={}",
                sale.getId(),
                sale.getStatus(),
                sale.getItems().size(),
                describeActiveItems(sale.getItems())
        );

        for (SaleItem item : sale.getItems()) {
            if (item == null || item.isDeleted()) {
                continue;
            }

            log.debug(
                    "SALE_INVENTORY_RESTORE_ITEM | saleId={} | productId={} | quantity={} | deleted={}",
                    sale.getId(),
                    item.getProductId(),
                    item.getQuantity(),
                    item.isDeleted()
            );

            tenantInventoryService.restoreStockFromSale(
                    sale.getId(),
                    item.getProductId(),
                    item.getQuantity()
            );
        }

        log.info(
                "SALE_INVENTORY_RESTORE_FINISH | saleId={} | status={}",
                sale.getId(),
                sale.getStatus()
        );
    }

    /**
     * Resolve e valida status obrigatório da venda.
     *
     * <p>Regra:
     * status nulo não é aceito, pois isso criaria ambiguidade operacional
     * e poderia mascarar falhas em fluxos que dependem do impacto em estoque.</p>
     *
     * @param status status recebido
     * @param operation nome da operação para log/erro
     * @return status resolvido
     */
    private SaleStatus resolveRequiredStatus(SaleStatus status, String operation) {
        if (status == null) {
            log.warn("SALE_STATUS_MISSING | operation={} | reason=null_status", operation);
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale status is required", 400);
        }
        return status;
    }

    /**
     * Define se o status da venda deve impactar inventory.
     *
     * @param status status da venda
     * @return true quando a venda deve afetar estoque
     */
    private boolean shouldAffectInventory(SaleStatus status) {
        if (status == null) {
            return false;
        }

        return switch (status) {
            case OPEN, CONFIRMED, PAID -> true;
            case DRAFT, CANCELLED -> false;
        };
    }

    /**
     * Soma o total de itens não deletados.
     *
     * @param items lista de itens
     * @return total calculado
     */
    private static BigDecimal sumItems(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (SaleItem it : items) {
            if (it == null || it.isDeleted()) {
                continue;
            }
            BigDecimal value = it.getTotalPrice();
            if (value == null) {
                continue;
            }
            total = total.add(value);
        }
        return total;
    }

    /**
     * Normaliza string opcional.
     *
     * @param value valor de entrada
     * @return string trimada ou null quando vazia
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Loga os itens do request, um a um.
     *
     * @param prefix prefixo do log
     * @param reqItems itens do request
     */
    private void logRequestItems(String prefix, List<SaleItemRequest> reqItems) {
        if (reqItems == null || reqItems.isEmpty()) {
            log.debug("{} | items=[]", prefix);
            return;
        }

        for (int i = 0; i < reqItems.size(); i++) {
            SaleItemRequest item = reqItems.get(i);
            if (item == null) {
                log.debug("{} | index={} | item=null", prefix, i);
                continue;
            }

            log.debug(
                    "{} | index={} | productId={} | productName={} | quantity={} | unitPrice={}",
                    prefix,
                    i,
                    item.productId(),
                    item.productName(),
                    item.quantity(),
                    item.unitPrice()
            );
        }
    }

    /**
     * Descreve itens para log.
     *
     * @param items lista de itens
     * @return string amigável para troubleshooting
     */
    private static String describeItems(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }

        List<String> parts = new ArrayList<>();
        for (SaleItem item : items) {
            if (item == null) {
                continue;
            }
            parts.add(
                    "{productId=" + item.getProductId()
                            + ", qty=" + item.getQuantity()
                            + ", deleted=" + item.isDeleted()
                            + ", total=" + item.getTotalPrice()
                            + "}"
            );
        }
        return parts.toString();
    }

    /**
     * Descreve somente itens ativos para log.
     *
     * @param items lista de itens
     * @return string contendo apenas itens ativos
     */
    private static String describeActiveItems(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }

        List<String> parts = new ArrayList<>();
        for (SaleItem item : items) {
            if (item == null || item.isDeleted()) {
                continue;
            }
            parts.add(
                    "{productId=" + item.getProductId()
                            + ", qty=" + item.getQuantity()
                            + ", total=" + item.getTotalPrice()
                            + "}"
            );
        }
        return parts.toString();
    }
}