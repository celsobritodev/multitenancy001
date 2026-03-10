package brito.com.multitenancy001.tenant.sales.api.mapper;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleItemResponse;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.domain.SaleItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Mapper de Sale para response da API.
 */
@Component
@Slf4j
public class SaleApiMapper {

    /**
     * Converte uma entidade Sale em SaleResponse.
     */
    public SaleResponse toResponse(Sale s) {
        if (s == null) return null;

        List<SaleItemResponse> items = (s.getItems() == null) ? List.of() : s.getItems().stream()
                .filter(i -> i != null && !i.isDeleted())
                .sorted(Comparator.comparing(SaleApiMapper::createdAtOrNull, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toItemResponse)
                .toList();

        log.debug("Mapeando sale para response. saleId={} items={}", s.getId(), items.size());

        return new SaleResponse(
                s.getId(),
                s.getSaleDate(),
                s.getTotalAmount(),
                s.getCustomerId(),
                s.getCustomerName(),
                s.getCustomerDocument(),
                s.getCustomerEmail(),
                s.getCustomerPhone(),
                s.getStatus(),
                items
        );
    }

    /**
     * Converte item de venda em response.
     */
    private SaleItemResponse toItemResponse(SaleItem i) {
        return new SaleItemResponse(
                i.getId(),
                i.getProductId(),
                i.getProductName(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getTotalPrice()
        );
    }

    /**
     * Extrai createdAt do item para ordenação determinística.
     */
    private static Instant createdAtOrNull(SaleItem i) {
        if (i == null) return null;
        AuditInfo audit = i.getAudit();
        if (audit == null) return null;
        return audit.getCreatedAt();
    }
}