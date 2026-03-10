package brito.com.multitenancy001.tenant.sales.app.command;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.customers.domain.Customer;
import brito.com.multitenancy001.tenant.customers.persistence.TenantCustomerRepository;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleCreateRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleItemRequest;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleResponse;
import brito.com.multitenancy001.tenant.sales.api.dto.SaleUpdateRequest;
import brito.com.multitenancy001.tenant.sales.api.mapper.SaleApiMapper;
import brito.com.multitenancy001.tenant.sales.domain.Sale;
import brito.com.multitenancy001.tenant.sales.domain.SaleItem;
import brito.com.multitenancy001.tenant.sales.persistence.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command use-cases for Sales (TENANT).
 *
 * <p>Integração com customers:</p>
 * <ul>
 *   <li>O request recebe {@code customerId}.</li>
 *   <li>O service resolve o customer real no módulo de customers.</li>
 *   <li>Grava o vínculo em {@code sale.customerId}.</li>
 *   <li>Grava snapshot em {@code customerName}, {@code customerDocument},
 *       {@code customerEmail} e {@code customerPhone}.</li>
 * </ul>
 *
 * <p>Notas:</p>
 * <ul>
 *   <li>Audit é controlado por {@code AuditEntityListener}.</li>
 *   <li>SaleItem exige {@code sale_id} obrigatório.</li>
 *   <li>Soft delete / restore segue o padrão do projeto.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSaleCommandService {

    private final TenantSchemaUnitOfWork uow;
    private final SaleRepository saleRepository;
    private final TenantCustomerRepository tenantCustomerRepository;
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

            log.info("🆕 Criando sale | accountId={} tenantSchema={} customerId={}",
                    accountId, tenantSchema, req.customerId());

            Sale sale = new Sale();
            sale.setSaleDate(req.saleDate());
            sale.setStatus(req.status());

            applyCustomerSnapshot(sale, req.customerId());

            List<SaleItem> items = buildItems(req.items(), sale);
            sale.setItems(items);
            sale.setTotalAmount(sumItems(items));

            Sale saved = saleRepository.save(sale);

            log.info("✅ Sale criada | saleId={} totalAmount={} customerId={}",
                    saved.getId(), saved.getTotalAmount(), saved.getCustomerId());

            return mapper.toResponse(saved);
        });
    }

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

            log.info("✏️ Atualizando sale | accountId={} tenantSchema={} saleId={} customerId={}",
                    accountId, tenantSchema, saleId, req.customerId());

            Sale sale = saleRepository.findByIdAndDeletedFalse(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

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

            if (sale.getItems() == null) {
                sale.setItems(new ArrayList<>());
            }
            sale.getItems().addAll(newItems);

            sale.setTotalAmount(sumItems(sale.getItems()));

            Sale saved = saleRepository.save(sale);

            log.info("✅ Sale atualizada | saleId={} totalAmount={} customerId={}",
                    saved.getId(), saved.getTotalAmount(), saved.getCustomerId());

            return mapper.toResponse(saved);
        });
    }

    /**
     * Deleta logicamente uma venda.
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

            log.info("🗑️ Deletando sale | accountId={} tenantSchema={} saleId={}",
                    accountId, tenantSchema, saleId);

            Sale sale = saleRepository.findByIdAndDeletedFalse(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            sale.softDelete();

            if (sale.getItems() != null) {
                for (SaleItem it : sale.getItems()) {
                    if (it == null) {
                        continue;
                    }
                    it.softDelete();
                }
            }

            saleRepository.save(sale);

            log.info("✅ Sale deletada | saleId={}", saleId);
            return null;
        });
    }

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

            log.info("♻️ Restaurando sale | accountId={} tenantSchema={} saleId={}",
                    accountId, tenantSchema, saleId);

            Sale sale = saleRepository.findById(saleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.SALE_NOT_FOUND, "sale not found", 404));

            sale.restore();

            if (sale.getItems() != null) {
                for (SaleItem it : sale.getItems()) {
                    if (it == null) {
                        continue;
                    }
                    it.restore();
                }
            }

            Sale saved = saleRepository.save(sale);

            log.info("✅ Sale restaurada | saleId={}", saved.getId());
            return mapper.toResponse(saved);
        });
    }

    /**
     * Busca o customer real e aplica snapshot na venda.
     *
     * <p>Quando {@code customerId} é nulo, a venda fica sem customer vinculado
     * e o snapshot é limpo.</p>
     *
     * @param sale venda alvo
     * @param customerId id do customer
     */
    private void applyCustomerSnapshot(Sale sale, UUID customerId) {
        if (sale == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "sale is required", 400);
        }

        if (customerId == null) {
            sale.setCustomerId(null);
            sale.setCustomerName(null);
            sale.setCustomerDocument(null);
            sale.setCustomerEmail(null);
            sale.setCustomerPhone(null);

            log.debug("Sale sem customer vinculado. Snapshot limpo.");
            return;
        }

        Customer customer = tenantCustomerRepository.findByIdNotDeleted(customerId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.SALE_CUSTOMER_NOT_FOUND,
                        "customer not found for sale: " + customerId,
                        404
                ));

        if (!customer.isActive()) {
            throw new ApiException(
                    ApiErrorCode.SALE_CUSTOMER_INACTIVE,
                    "customer inactive for sale: " + customerId,
                    409
            );
        }

        sale.setCustomerId(customer.getId());
        sale.setCustomerName(trimToNull(customer.getName()));
        sale.setCustomerDocument(trimToNull(customer.getDocument()));
        sale.setCustomerEmail(trimToNull(customer.getEmail()));
        sale.setCustomerPhone(trimToNull(customer.getPhone()));

        log.debug("Snapshot de customer aplicado em sale | customerId={} name={}",
                customer.getId(), customer.getName());
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
            BigDecimal v = it.getTotalPrice();
            if (v == null) {
                continue;
            }
            total = total.add(v);
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