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
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Criar vendas.</li>
 *   <li>Atualizar vendas existentes.</li>
 *   <li>Deletar logicamente vendas.</li>
 *   <li>Restaurar vendas deletadas logicamente.</li>
 *   <li>Aplicar snapshot do customer na venda.</li>
 *   <li>Construir e validar itens.</li>
 *   <li>Integrar o fluxo de vendas com o módulo de Inventory.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>Executa mutações via {@link TenantSchemaUnitOfWork}.</li>
 *   <li>Usa {@link AppClock} como única fonte de tempo da aplicação.</li>
 *   <li>Controllers não acessam repositories diretamente.</li>
 *   <li>Valida entidades relacionadas antes de persistir.</li>
 * </ul>
 *
 * <p>Regra de integração com Inventory:</p>
 * <ul>
 *   <li>Vendas em {@code OPEN}, {@code CONFIRMED} e {@code PAID} afetam estoque.</li>
 *   <li>Vendas em {@code DRAFT} e {@code CANCELLED} não afetam estoque.</li>
 *   <li>Atualização restaura o estoque anterior antes de aplicar os novos itens.</li>
 *   <li>Delete restaura estoque.</li>
 *   <li>Restore reaplica consumo de estoque, quando aplicável.</li>
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

            log.info(
                    "Creating sale | accountId={} | tenantSchema={} | customerId={} | status={}",
                    accountId,
                    tenantSchema,
                    req.customerId(),
                    req.status()
            );

            Sale sale = new Sale();
            sale.setSaleDate(req.saleDate());
            sale.setStatus(req.status());

            applyCustomerSnapshot(sale, req.customerId());

            List<SaleItem> items = buildItems(req.items(), sale);
            validateItemsAgainstProducts(items);

            sale.setItems(items);
            sale.setTotalAmount(sumItems(items));

            Sale saved = saleRepository.save(sale);

            log.debug(
                    "Sale persisted before inventory write | saleId={} | itemsCount={} | totalAmount={}",
                    saved.getId(),
                    saved.getItems() != null ? saved.getItems().size() : 0,
                    saved.getTotalAmount()
            );

            applyInventoryForSaleWrite(saved);

            log.info(
                    "Sale created successfully | saleId={} | totalAmount={} | customerId={} | status={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getCustomerId(),
                    saved.getStatus()
            );

            return mapper.toResponse(saved);
        });
    }

    /**
     * Atualiza uma venda existente.
     *
     * <p>Regra de inventory:</p>
     * <ul>
     *   <li>Primeiro restaura o estoque dos itens ativos anteriores, se o status antigo afetava estoque.</li>
     *   <li>Depois aplica os novos itens, se o novo status afetar estoque.</li>
     * </ul>
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

            log.info(
                    "Updating sale | accountId={} | tenantSchema={} | saleId={} | customerId={} | newStatus={}",
                    accountId,
                    tenantSchema,
                    saleId,
                    req.customerId(),
                    req.status()
            );

            Sale sale = saleRepository.findByIdAndDeletedFalse(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            log.debug(
                    "Loaded sale for update | saleId={} | currentStatus={} | deleted={} | itemsCount={}",
                    sale.getId(),
                    sale.getStatus(),
                    sale.isDeleted(),
                    sale.getItems() != null ? sale.getItems().size() : 0
            );

            restoreInventoryForCurrentActiveItems(sale);

            sale.setSaleDate(req.saleDate());
            sale.setStatus(req.status());

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

            Sale saved = saleRepository.save(sale);

            log.debug(
                    "Sale persisted after update before inventory write | saleId={} | totalAmount={} | status={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getStatus()
            );

            applyInventoryForSaleWrite(saved);

            log.info(
                    "Sale updated successfully | saleId={} | totalAmount={} | customerId={} | status={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getCustomerId(),
                    saved.getStatus()
            );

            return mapper.toResponse(saved);
        });
    }

    /**
     * Deleta logicamente uma venda.
     *
     * <p>Regra:</p>
     * restaura estoque dos itens ativos antes do soft delete da venda,
     * se o status da venda afetava inventory.
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
                    "Deleting sale | accountId={} | tenantSchema={} | saleId={}",
                    accountId,
                    tenantSchema,
                    saleId
            );

            Sale sale = saleRepository.findByIdAndDeletedFalse(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            log.debug(
                    "Loaded sale for delete | saleId={} | status={} | itemsCount={}",
                    sale.getId(),
                    sale.getStatus(),
                    sale.getItems() != null ? sale.getItems().size() : 0
            );

            restoreInventoryForCurrentActiveItems(sale);

            sale.softDelete();

            saleRepository.save(sale);

            log.info("Sale deleted successfully | saleId={}", saleId);
            return null;
        });
    }

    /**
     * Restaura uma venda deletada logicamente.
     *
     * <p>Regra:</p>
     * reativa a venda e reaplica consumo de estoque dos itens ativos
     * se o status restaurado afetar inventory.
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
                    "Restoring sale | accountId={} | tenantSchema={} | saleId={}",
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
                    "Loaded sale for restore | saleId={} | status={} | deleted={}",
                    sale.getId(),
                    sale.getStatus(),
                    sale.isDeleted()
            );

            sale.restore();

            Sale saved = saleRepository.save(sale);

            log.debug(
                    "Sale restored in persistence before inventory write | saleId={} | status={}",
                    saved.getId(),
                    saved.getStatus()
            );

            applyInventoryForSaleWrite(saved);

            log.info(
                    "Sale restored successfully | saleId={} | totalAmount={} | status={}",
                    saved.getId(),
                    saved.getTotalAmount(),
                    saved.getStatus()
            );

            return mapper.toResponse(saved);
        });
    }

    /**
     * Preenche snapshot de customer na venda.
     *
     * <p>Quando o customer é nulo, os campos de snapshot são limpos.</p>
     *
     * @param sale venda alvo
     * @param customerId customer opcional
     */
    private void applyCustomerSnapshot(Sale sale, UUID customerId) {
        if (sale == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale is required", 400);
        }

        if (customerId == null) {
            log.debug("Clearing customer snapshot for sale because customerId is null");

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
                "Customer snapshot applied to sale | customerId={} | customerName={}",
                customer.getId(),
                customer.getName()
        );
    }

    /**
     * Constrói os itens da venda garantindo o vínculo obrigatório com a sale.
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
     * Valida existência dos produtos e campos básicos dos itens.
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
     * se o status da venda afetar estoque.
     *
     * @param sale venda salva
     */
    private void applyInventoryForSaleWrite(Sale sale) {
        if (sale == null || sale.getItems() == null || sale.getItems().isEmpty()) {
            log.debug("Skipping inventory write because sale or items are empty");
            return;
        }

        if (!shouldAffectInventory(sale.getStatus())) {
            log.info(
                    "Sale does not affect inventory | saleId={} | status={}",
                    sale.getId(),
                    sale.getStatus()
            );
            return;
        }

        log.info(
                "Applying inventory write for sale | saleId={} | status={} | itemsCount={}",
                sale.getId(),
                sale.getStatus(),
                sale.getItems().size()
        );

        for (SaleItem item : sale.getItems()) {
            if (item == null || item.isDeleted()) {
                continue;
            }

            log.debug(
                    "Consuming stock for sale item | saleId={} | productId={} | quantity={}",
                    sale.getId(),
                    item.getProductId(),
                    item.getQuantity()
            );

            tenantInventoryService.consumeStockForSale(
                    sale.getId(),
                    item.getProductId(),
                    item.getQuantity()
            );
        }
    }

    /**
     * Restaura inventory dos itens ativos atuais da venda,
     * se o status atual da venda afetar estoque.
     *
     * @param sale venda
     */
    private void restoreInventoryForCurrentActiveItems(Sale sale) {
        if (sale == null || sale.getItems() == null || sale.getItems().isEmpty()) {
            log.debug("Skipping inventory restore because sale or items are empty");
            return;
        }

        if (!shouldAffectInventory(sale.getStatus())) {
            log.info(
                    "Sale does not currently affect inventory | saleId={} | status={}",
                    sale.getId(),
                    sale.getStatus()
            );
            return;
        }

        log.info(
                "Restoring inventory for current sale items | saleId={} | status={} | itemsCount={}",
                sale.getId(),
                sale.getStatus(),
                sale.getItems().size()
        );

        for (SaleItem item : sale.getItems()) {
            if (item == null || item.isDeleted()) {
                continue;
            }

            log.debug(
                    "Restoring stock for sale item | saleId={} | productId={} | quantity={}",
                    sale.getId(),
                    item.getProductId(),
                    item.getQuantity()
            );

            tenantInventoryService.restoreStockFromSale(
                    sale.getId(),
                    item.getProductId(),
                    item.getQuantity()
            );
        }
    }

    /**
     * Define se o status da venda deve impactar inventory.
     *
     * <p>Regra adotada:</p>
     * <ul>
     *   <li>DRAFT: não afeta estoque.</li>
     *   <li>OPEN: afeta estoque.</li>
     *   <li>CONFIRMED: afeta estoque.</li>
     *   <li>PAID: afeta estoque.</li>
     *   <li>CANCELLED: não afeta estoque.</li>
     * </ul>
     *
     * @param status status da venda
     * @return true se deve impactar inventory
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
     * Soma o total dos itens não deletados.
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
            if (it == null) {
                continue;
            }
            if (it.isDeleted()) {
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
     * @return valor trimado ou null quando vazio
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}