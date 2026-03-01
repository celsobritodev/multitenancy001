package brito.com.multitenancy001.tenant.sales.app.command;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleCreateRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleItemRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleUpdateRequest;
import brito.com.multitenancy001.tenant.sales.api.mapper.SaleApiMapper;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.domain.SaleItem;
import brito.com.multitenancy001.tenant.sales.persistence.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command use-cases for Sales (TENANT).
 *
 * Notes:
 * - This project uses AuditEntityListener + AuditInfo (no setCreatedAt/setUpdatedAt/setDeletedAt calls).
 * - SaleItem has mandatory relation to Sale (sale_id NOT NULL), so we must set sale on each item.
 * - Soft delete / restore follows SoftDeletable pattern (softDelete/restore).
 */
@Service
@RequiredArgsConstructor
public class TenantSaleCommandService {

    private final TenantSchemaUnitOfWork uow;
    private final SaleRepository saleRepository;
    private final SaleApiMapper mapper;
    private final AppClock appClock;

    public SaleResponse create(Long accountId, String tenantSchema, SaleCreateRequest req) {
        if (req == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request is required", 400);

        return uow.tx(tenantSchema, () -> {
            // now is not directly used for audit fields (listener handles it), but keeping AppClock usage is ok
            appClock.instant();

            Sale sale = new Sale();
            sale.setSaleDate(req.saleDate());
            sale.setCustomerName(req.customerName());
            sale.setCustomerDocument(req.customerDocument());
            sale.setCustomerEmail(req.customerEmail());
            sale.setCustomerPhone(req.customerPhone());
            sale.setStatus(req.status());

            // items (must link to sale)
            List<SaleItem> items = buildItems(req.items(), sale);
            sale.setItems(items);

            // totals
            sale.setTotalAmount(sumItems(items));

            Sale saved = saleRepository.save(sale);
            return mapper.toResponse(saved);
        });
    }

    public SaleResponse update(Long accountId, String tenantSchema, UUID saleId, SaleUpdateRequest req) {
        if (saleId == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale id is required", 400);
        if (req == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request is required", 400);

        return uow.tx(tenantSchema, () -> {
            appClock.instant();

            Sale sale = saleRepository.findByIdAndDeletedFalse(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            sale.setSaleDate(req.saleDate());
            sale.setCustomerName(req.customerName());
            sale.setCustomerDocument(req.customerDocument());
            sale.setCustomerEmail(req.customerEmail());
            sale.setCustomerPhone(req.customerPhone());
            sale.setStatus(req.status());

            // replace items: soft-delete old + add new
            if (sale.getItems() != null) {
                for (SaleItem old : sale.getItems()) {
                    if (old == null) continue;
                    old.softDelete();
                }
            }

            List<SaleItem> newItems = buildItems(req.items(), sale);

            if (sale.getItems() == null) {
                sale.setItems(new ArrayList<>());
            }
            sale.getItems().addAll(newItems);

            sale.setTotalAmount(sumItems(sale.getItems()));

            Sale saved = saleRepository.save(sale);
            return mapper.toResponse(saved);
        });
    }

    public void delete(Long accountId, String tenantSchema, UUID saleId) {
        if (saleId == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale id is required", 400);

        uow.tx(tenantSchema, () -> {
            appClock.instant();

            Sale sale = saleRepository.findByIdAndDeletedFalse(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            // Prefer using domain softDelete if available
            // If your Sale entity does not have softDelete(), replace with:
            // sale.setDeleted(true);
            // sale.getAudit().markDeleted(...?) (but usually listener handles)
            trySoftDeleteSale(sale);

            // soft-delete items too
            if (sale.getItems() != null) {
                for (SaleItem it : sale.getItems()) {
                    if (it == null) continue;
                    it.softDelete();
                }
            }

            saleRepository.save(sale);
            return null;
        });
    }

    public SaleResponse restore(Long accountId, String tenantSchema, UUID saleId) {
        if (saleId == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale id is required", 400);

        return uow.tx(tenantSchema, () -> {
            appClock.instant();

            Sale sale = saleRepository.findById(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            tryRestoreSale(sale);

            if (sale.getItems() != null) {
                for (SaleItem it : sale.getItems()) {
                    if (it == null) continue;
                    it.restore();
                }
            }

            Sale saved = saleRepository.save(sale);
            return mapper.toResponse(saved);
        });
    }

    private static List<SaleItem> buildItems(List<SaleItemRequest> reqItems, Sale sale) {
        if (reqItems == null || reqItems.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "items is required", 400);
        }
        if (sale == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale is required to build items", 400);
        }

        List<SaleItem> out = new ArrayList<>();
        for (SaleItemRequest r : reqItems) {
            if (r == null) continue;

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale); // IMPORTANT: sale_id NOT NULL

            saleItem.setProductId(r.productId());
            saleItem.setProductName(r.productName());

            saleItem.setQuantity(r.quantity());
            saleItem.setUnitPrice(r.unitPrice());

            saleItem.recalcTotal();

            // Deleted/audit default handled by entity defaults + listener
            saleItem.setDeleted(false);

            out.add(saleItem);
        }

        if (out.isEmpty()) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "items is required", 400);
        return out;
    }

    private static BigDecimal sumItems(List<SaleItem> items) {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;
        for (SaleItem it : items) {
            if (it == null) continue;
            if (it.isDeleted()) continue;

            BigDecimal v = it.getTotalPrice();
            if (v == null) continue;

            total = total.add(v);
        }
        return total;
    }

    /**
     * Keeps this service compatible with your Sale implementation.
     *
     * If Sale has softDelete()/restore(): use them.
     * If not, fallback to setDeleted(true/false) and audit clearDeleted.
     */
    private static void trySoftDeleteSale(Sale sale) {
        try {
            sale.getClass().getMethod("softDelete").invoke(sale);
            return;
        } catch (Exception ignore) {
            // fallback
        }

        try {
            sale.getClass().getMethod("setDeleted", boolean.class).invoke(sale, true);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Sale has no softDelete nor setDeleted(true)", 500);
        }
    }

    private static void tryRestoreSale(Sale sale) {
        try {
            sale.getClass().getMethod("restore").invoke(sale);
            return;
        } catch (Exception ignore) {
            // fallback
        }

        // fallback: setDeleted(false) + clear deleted in audit if exists
        try {
            sale.getClass().getMethod("setDeleted", boolean.class).invoke(sale, false);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Sale has no restore nor setDeleted(false)", 500);
        }

        try {
            Object audit = sale.getClass().getMethod("getAudit").invoke(sale);
            if (audit != null) {
                audit.getClass().getMethod("clearDeleted").invoke(audit);
            }
        } catch (Exception ignore) {
            // ok: if audit not present, nothing to clear
        }
    }
}