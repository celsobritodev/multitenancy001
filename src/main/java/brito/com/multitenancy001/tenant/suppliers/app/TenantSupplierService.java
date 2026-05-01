package brito.com.multitenancy001.tenant.suppliers.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.suppliers.app.command.CreateSupplierCommand;
import brito.com.multitenancy001.tenant.suppliers.app.command.UpdateSupplierCommand;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import brito.com.multitenancy001.tenant.suppliers.persistence.TenantSupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application Service para Suppliers (contexto Tenant).
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Orquestrar casos de uso (commands)</li>
 *   <li>Aplicar regras de negócio (unicidade, bloqueios por deleted, etc.)</li>
 *   <li>Delegar regras intrínsecas ao domínio quando aplicável</li>
 * </ul>
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem status HTTP hardcoded</li>
 *   <li>Sem alteração de comportamento</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSupplierService {

    private final TenantSupplierRepository tenantSupplierRepository;

    // =========================================================
    // READ
    // =========================================================

    @TenantReadOnlyTx
    public Supplier findById(UUID id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório");
        }

        Supplier s = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.SUPPLIER_NOT_FOUND,
                        "Fornecedor não encontrado com ID: " + id
                ));

        if (s.isDeleted()) {
            throw new ApiException(ApiErrorCode.SUPPLIER_DELETED, "Fornecedor removido");
        }

        return s;
    }

    @TenantReadOnlyTx
    public List<Supplier> findAll() {
        return tenantSupplierRepository.findAllNotDeleted();
    }

    @TenantReadOnlyTx
    public List<Supplier> findActive() {
        return tenantSupplierRepository.findAllActiveNotDeleted();
    }

    @TenantReadOnlyTx
    public Supplier findByDocument(String document) {
        if (!StringUtils.hasText(document)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_DOCUMENT_REQUIRED, "document é obrigatório");
        }

        return tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(document.trim())
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.SUPPLIER_NOT_FOUND,
                        "Fornecedor não encontrado"
                ));
    }

    @TenantReadOnlyTx
    public List<Supplier> searchByName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_NAME_REQUIRED, "name é obrigatório");
        }

        return tenantSupplierRepository.searchNotDeletedByName(name.trim());
    }

    @TenantReadOnlyTx
    public List<Supplier> findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_EMAIL_REQUIRED, "email é obrigatório");
        }

        return tenantSupplierRepository.findNotDeletedByEmail(email.trim());
    }

    // =========================================================
    // WRITE
    // =========================================================

    @TenantTx
    public Supplier create(CreateSupplierCommand cmd) {
        Supplier supplier = mapToNewEntity(cmd);
        validateForCreate(supplier);

        if (StringUtils.hasText(supplier.getDocument())) {
            String doc = supplier.getDocument().trim();

            Optional<Supplier> existing = tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(doc);
            if (existing.isPresent()) {
                throw new ApiException(
                        ApiErrorCode.SUPPLIER_DOCUMENT_ALREADY_EXISTS,
                        "Já existe fornecedor com document: " + doc
                );
            }

            supplier.setDocument(doc);
        } else {
            supplier.setDocument(null);
        }

        supplier.setDeleted(false);
        supplier.setActive(true);

        return tenantSupplierRepository.save(supplier);
    }

    @TenantTx
    public Supplier update(UUID id, UpdateSupplierCommand cmd) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório");
        }
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.SUPPLIER_REQUIRED, "payload é obrigatório");
        }

        Supplier existing = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.SUPPLIER_NOT_FOUND,
                        "Fornecedor não encontrado com ID: " + id
                ));

        if (existing.isDeleted()) {
            throw new ApiException(ApiErrorCode.SUPPLIER_DELETED, "Não é permitido alterar fornecedor deletado");
        }

        if (cmd.name() != null) {
            if (!StringUtils.hasText(cmd.name())) {
                throw new ApiException(ApiErrorCode.SUPPLIER_NAME_REQUIRED, "name é obrigatório");
            }
            existing.setName(cmd.name().trim());
        }

        if (cmd.contactPerson() != null) {
            existing.setContactPerson(StringUtils.hasText(cmd.contactPerson()) ? cmd.contactPerson().trim() : null);
        }

        if (cmd.email() != null) {
            existing.setEmail(StringUtils.hasText(cmd.email()) ? cmd.email().trim() : null);
        }

        if (cmd.phone() != null) {
            existing.setPhone(StringUtils.hasText(cmd.phone()) ? cmd.phone().trim() : null);
        }

        if (cmd.address() != null) {
            existing.setAddress(StringUtils.hasText(cmd.address()) ? cmd.address().trim() : null);
        }

        if (cmd.document() != null) {
            String newDoc = cmd.document();

            if (StringUtils.hasText(newDoc)) {
                newDoc = newDoc.trim();

                Optional<Supplier> other = tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(newDoc);
                if (other.isPresent() && !other.get().getId().equals(id)) {
                    throw new ApiException(
                            ApiErrorCode.SUPPLIER_DOCUMENT_ALREADY_EXISTS,
                            "Já existe fornecedor com document: " + newDoc
                    );
                }

                existing.setDocument(newDoc);
            } else {
                existing.setDocument(null);
            }
        }

        if (cmd.leadTimeDays() != null) {
            if (cmd.leadTimeDays() < 0) {
                throw new ApiException(ApiErrorCode.INVALID_LEAD_TIME, "leadTimeDays não pode ser negativo");
            }
            existing.setLeadTimeDays(cmd.leadTimeDays());
        }

        if (cmd.rating() != null) {
            validateRating(cmd.rating());
            existing.setRating(cmd.rating());
        }

        return tenantSupplierRepository.save(existing);
    }

    @TenantTx
    public Supplier toggleActive(UUID id) {
        if (id == null) {
            throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório");
        }

        Supplier s = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.SUPPLIER_NOT_FOUND,
                        "Fornecedor não encontrado com ID: " + id
                ));

        if (s.isDeleted()) {
            throw new ApiException(ApiErrorCode.SUPPLIER_DELETED, "Fornecedor removido");
        }

        s.setActive(!s.isActive());
        return tenantSupplierRepository.save(s);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Supplier mapToNewEntity(CreateSupplierCommand cmd) {
        if (cmd == null) {
            throw new ApiException(ApiErrorCode.SUPPLIER_REQUIRED, "Fornecedor é obrigatório");
        }

        return Supplier.builder()
                .name(cmd.name())
                .document(cmd.document())
                .email(cmd.email())
                .leadTimeDays(cmd.leadTimeDays())
                .rating(cmd.rating())
                .build();
    }

    private void validateForCreate(Supplier supplier) {
        if (supplier == null) {
            throw new ApiException(ApiErrorCode.SUPPLIER_REQUIRED, "Fornecedor é obrigatório");
        }

        if (!StringUtils.hasText(supplier.getName())) {
            throw new ApiException(ApiErrorCode.SUPPLIER_NAME_REQUIRED, "name é obrigatório");
        }

        if (supplier.getLeadTimeDays() != null && supplier.getLeadTimeDays() < 0) {
            throw new ApiException(ApiErrorCode.INVALID_LEAD_TIME, "leadTimeDays não pode ser negativo");
        }

        if (supplier.getRating() != null) {
            validateRating(supplier.getRating());
        }
    }

    private void validateRating(BigDecimal rating) {
        if (rating.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_RATING, "rating não pode ser negativo");
        }
        if (rating.compareTo(new BigDecimal("9.99")) > 0) {
            throw new ApiException(ApiErrorCode.INVALID_RATING, "rating máximo é 9.99");
        }
    }
}