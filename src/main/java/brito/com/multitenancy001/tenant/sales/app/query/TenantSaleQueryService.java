package brito.com.multitenancy001.tenant.sales.app.query;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.mapper.SaleApiMapper;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.domain.SaleStatus;
import brito.com.multitenancy001.tenant.sales.persistence.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Query use-cases for Sales (TENANT).
 */
@Service
@RequiredArgsConstructor
public class TenantSaleQueryService {

    private final TenantSchemaUnitOfWork uow;
    private final SaleRepository saleRepository;
    private final SaleApiMapper mapper;

    public SaleResponse getById(Long accountId, String tenantSchema, UUID id) {
        if (id == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale id is required", 400);

        return uow.readOnly(tenantSchema, () -> {
            Sale s = saleRepository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));
            return mapper.toResponse(s);
        });
    }

    public List<SaleResponse> list(Long accountId, String tenantSchema, Instant from, Instant to, String status) {
        return uow.readOnly(tenantSchema, () -> {
            List<Sale> list;

            boolean hasRange = (from != null && to != null);
            if (hasRange) {
                list = saleRepository.findByDeletedFalseAndSaleDateBetweenOrderBySaleDateDesc(from, to);
            } else {
                list = saleRepository.findByDeletedFalseOrderBySaleDateDesc();
            }

            SaleStatus st = parseStatusOrNull(status);
            if (st != null) {
                list = list.stream().filter(s -> s != null && s.getStatus() == st).toList();
            }

            return list.stream().map(mapper::toResponse).toList();
        });
    }

    private static SaleStatus parseStatusOrNull(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return SaleStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignore) {
            return null;
        }
    }
}