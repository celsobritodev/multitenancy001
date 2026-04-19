package brito.com.multitenancy001.tenant.sales.app.command;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.customers.domain.Customer;
import brito.com.multitenancy001.tenant.customers.persistence.TenantCustomerRepository;
import brito.com.multitenancy001.tenant.inventory.app.TenantInventoryService;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleItemRequest;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.domain.SaleItem;
import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper de mutação do módulo Sales.
 *
 * <p>Centraliza regras compartilhadas entre create/update/delete/restore:</p>
 * <ul>
 *   <li>snapshot de customer</li>
 *   <li>construção e validação de itens</li>
 *   <li>cálculo de total</li>
 *   <li>integração com inventory</li>
 *   <li>resolução de status</li>
 *   <li>helpers de observabilidade</li>
 * </ul>
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>remover multi-responsabilidade da fachada de command</li>
 *   <li>reutilizar regras sem duplicação</li>
 *   <li>preservar troubleshooting com logs claros</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantSaleMutationHelper {

    private final TenantCustomerRepository tenantCustomerRepository;
    private final TenantProductRepository tenantProductRepository;
    private final TenantInventoryService tenantInventoryService;

    /**
     * Preenche snapshot de customer na venda.
     *
     * <p>Quando o customerId é nulo, os campos de snapshot são limpos.</p>
     *
     * @param sale venda alvo
     * @param customerId customer opcional
     */
    public void applyCustomerSnapshot(Sale sale, UUID customerId) {
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
    public List<SaleItem> buildItems(List<SaleItemRequest> reqItems, Sale sale) {
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
    public void validateItemsAgainstProducts(List<SaleItem> items) {
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
    public void applyInventoryForSaleWrite(Sale sale) {
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
    public void restoreInventoryForCurrentActiveItems(Sale sale) {
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
     * <p>Regra:</p>
     * status nulo não é aceito, pois isso criaria ambiguidade operacional
     * e poderia mascarar falhas em fluxos que dependem do impacto em estoque.
     *
     * @param status status recebido
     * @param operation nome da operação para log/erro
     * @return status resolvido
     */
    public SaleStatus resolveRequiredStatus(SaleStatus status, String operation) {
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
    public boolean shouldAffectInventory(SaleStatus status) {
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
    public BigDecimal sumItems(List<SaleItem> items) {
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
     * Loga os itens do request, um a um.
     *
     * @param prefix prefixo do log
     * @param reqItems itens do request
     */
    public void logRequestItems(String prefix, List<SaleItemRequest> reqItems) {
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
     * Descreve itens para log em formato legível e sem pseudo-JSON.
     *
     * @param items lista de itens
     * @return string amigável para troubleshooting
     */
    public String describeItems(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }

        List<String> parts = new ArrayList<>();
        for (SaleItem item : items) {
            if (item == null) {
                continue;
            }
            parts.add(describeItem(item, true));
        }
        return parts.toString();
    }

    /**
     * Descreve somente itens ativos para log em formato legível e sem pseudo-JSON.
     *
     * @param items lista de itens
     * @return string contendo apenas itens ativos
     */
    public String describeActiveItems(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }

        List<String> parts = new ArrayList<>();
        for (SaleItem item : items) {
            if (item == null || item.isDeleted()) {
                continue;
            }
            parts.add(describeItem(item, false));
        }
        return parts.toString();
    }

    /**
     * Gera representação textual estável de item para logs, evitando pseudo-JSON.
     *
     * @param item item da venda
     * @param includeDeleted indica se o campo deleted deve ser incluído
     * @return descrição textual do item
     */
    private String describeItem(SaleItem item, boolean includeDeleted) {
        StringBuilder sb = new StringBuilder("SaleItem(")
                .append("productId=").append(item.getProductId())
                .append(", qty=").append(item.getQuantity());

        if (includeDeleted) {
            sb.append(", deleted=").append(item.isDeleted());
        }

        sb.append(", total=").append(item.getTotalPrice())
                .append(")");

        return sb.toString();
    }

    /**
     * Normaliza string opcional.
     *
     * @param value valor de entrada
     * @return string trimada ou null quando vazia
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}