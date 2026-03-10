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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Query use-cases for Sales (TENANT).
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Buscar venda por ID.</li>
 *   <li>Listar vendas com paginação.</li>
 *   <li>Filtrar por período.</li>
 *   <li>Filtrar por status.</li>
 *   <li>Filtrar por customerId quando informado.</li>
 * </ul>
 *
 * <p>Observações:</p>
 * <ul>
 *   <li>As leituras sempre executam dentro do tenant schema correto.</li>
 *   <li>As respostas da API são sempre DTOs mapeados por {@link SaleApiMapper}.</li>
 *   <li>O filtro de status é aplicado no banco.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSaleQueryService {

    private final TenantSchemaUnitOfWork uow;
    private final SaleRepository saleRepository;
    private final SaleApiMapper mapper;

    /**
     * Busca uma venda não deletada por ID.
     *
     * @param accountId account do tenant atual
     * @param tenantSchema schema do tenant atual
     * @param id identificador da venda
     * @return venda encontrada
     */
    public SaleResponse getById(Long accountId, String tenantSchema, UUID id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale id is required", 400);
        }

        return uow.readOnly(tenantSchema, () -> {
            log.info("🔎 Buscando sale por id | accountId={} tenantSchema={} saleId={}",
                    accountId, tenantSchema, id);

            Sale s = saleRepository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            log.info("✅ Sale encontrada | saleId={} customerId={} status={}",
                    s.getId(), s.getCustomerId(), s.getStatus());

            return mapper.toResponse(s);
        });
    }

    /**
     * Lista vendas do tenant com filtros opcionais e paginação.
     *
     * @param accountId account do tenant atual
     * @param tenantSchema schema do tenant atual
     * @param from data inicial opcional
     * @param to data final opcional
     * @param status status opcional
     * @param customerId customer opcional
     * @param pageable paginação/sort
     * @return página de vendas filtradas
     */
    public Page<SaleResponse> list(Long accountId,
                                   String tenantSchema,
                                   Instant from,
                                   Instant to,
                                   String status,
                                   UUID customerId,
                                   Pageable pageable) {

        return uow.readOnly(tenantSchema, () -> {
            log.info("📋 Listando sales | accountId={} tenantSchema={} from={} to={} status={} customerId={} pageable={}",
                    accountId, tenantSchema, from, to, status, customerId, pageable);

            SaleStatus parsedStatus = parseStatusOrNull(status);

            Page<Sale> page = resolvePage(from, to, parsedStatus, customerId, pageable);

            Page<SaleResponse> out = page.map(mapper::toResponse);

            log.info("✅ Sales listadas | totalElements={} totalPages={} page={} size={} statusFiltrado={} customerIdFiltrado={}",
                    out.getTotalElements(),
                    out.getTotalPages(),
                    out.getNumber(),
                    out.getSize(),
                    parsedStatus,
                    customerId);

            return out;
        });
    }

    /**
     * Resolve a consulta paginada no repositório conforme os filtros informados.
     *
     * @param from data inicial opcional
     * @param to data final opcional
     * @param status status opcional já parseado
     * @param customerId customer opcional
     * @param pageable paginação/sort
     * @return página de entidades Sale
     */
    private Page<Sale> resolvePage(Instant from,
                                   Instant to,
                                   SaleStatus status,
                                   UUID customerId,
                                   Pageable pageable) {

        boolean hasRange = (from != null && to != null);
        boolean hasStatus = (status != null);
        boolean hasCustomer = (customerId != null);

        if (hasRange && hasStatus && hasCustomer) {
            return saleRepository.findByDeletedFalseAndCustomerIdAndStatusAndSaleDateBetweenOrderBySaleDateDesc(
                    customerId, status, from, to, pageable
            );
        }

        if (hasRange && hasStatus) {
            return saleRepository.findByDeletedFalseAndStatusAndSaleDateBetweenOrderBySaleDateDesc(
                    status, from, to, pageable
            );
        }

        if (hasRange && hasCustomer) {
            return saleRepository.findByDeletedFalseAndCustomerIdAndSaleDateBetweenOrderBySaleDateDesc(
                    customerId, from, to, pageable
            );
        }

        if (hasStatus && hasCustomer) {
            return saleRepository.findByDeletedFalseAndCustomerIdAndStatusOrderBySaleDateDesc(
                    customerId, status, pageable
            );
        }

        if (hasRange) {
            return saleRepository.findByDeletedFalseAndSaleDateBetweenOrderBySaleDateDesc(from, to, pageable);
        }

        if (hasStatus) {
            return saleRepository.findByDeletedFalseAndStatusOrderBySaleDateDesc(status, pageable);
        }

        if (hasCustomer) {
            return saleRepository.findByDeletedFalseAndCustomerIdOrderBySaleDateDesc(customerId, pageable);
        }

        return saleRepository.findByDeletedFalseOrderBySaleDateDesc(pageable);
    }

    /**
     * Faz parse tolerante do status.
     *
     * @param raw texto recebido no request
     * @return status convertido ou null quando ausente/inválido
     */
    private static SaleStatus parseStatusOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        try {
            return SaleStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignore) {
            return null;
        }
    }
}