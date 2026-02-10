package brito.com.multitenancy001.tenant.suppliers.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSupplierService {

    private final TenantSupplierRepository tenantSupplierRepository;
    private final AppClock appClock;

    // =========================================================
    // READ (por padrão: NÃO retorna deletados)
    // =========================================================

    @TenantReadOnlyTx
    public Supplier findById(UUID id) {
        if (id == null) throw new ApiException("SUPPLIER_ID_REQUIRED", "id é obrigatório", 400);

        Supplier s = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND",
                        "Fornecedor não encontrado com ID: " + id, 404));

        if (s.isDeleted()) {
            throw new ApiException("SUPPLIER_DELETED", "Fornecedor deletado não pode ser consultado", 404);
        }

        return s;
    }

    @TenantReadOnlyTx
    public List<Supplier> findAll() {
        return tenantSupplierRepository.findNotDeleted();
    }

    @TenantReadOnlyTx
    public List<Supplier> findActive() {
        return tenantSupplierRepository.findActiveNotDeleted();
    }

    @TenantReadOnlyTx
    public Supplier findByDocument(String document) {
        if (!StringUtils.hasText(document)) {
            throw new ApiException("SUPPLIER_DOCUMENT_REQUIRED", "document é obrigatório", 400);
        }

        String doc = document.trim();

        return tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(doc)
                .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND",
                        "Fornecedor não encontrado com document: " + doc, 404));
    }

    @TenantReadOnlyTx
    public List<Supplier> searchByName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException("SUPPLIER_NAME_REQUIRED", "name é obrigatório", 400);
        }
        return tenantSupplierRepository.findNotDeletedByNameContainingIgnoreCase(name.trim());
    }

    @TenantReadOnlyTx
    public List<Supplier> findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException("SUPPLIER_EMAIL_REQUIRED", "email é obrigatório", 400);
        }
        return tenantSupplierRepository.findNotDeletedByEmail(email.trim());
    }

    // =========================================================
    // WRITE
    // =========================================================

    @TenantTx
    public Supplier create(Supplier supplier) {
        validateForCreate(supplier);

        if (StringUtils.hasText(supplier.getDocument())) {
            String doc = supplier.getDocument().trim();
            Optional<Supplier> existing = tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(doc);
            if (existing.isPresent()) {
                throw new ApiException("SUPPLIER_DOCUMENT_ALREADY_EXISTS",
                        "Já existe fornecedor com document: " + doc, 409);
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
    public Supplier update(UUID id, Supplier req) {
        if (id == null) throw new ApiException("SUPPLIER_ID_REQUIRED", "id é obrigatório", 400);
        if (req == null) throw new ApiException("SUPPLIER_REQUIRED", "payload é obrigatório", 400);

        Supplier existing = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND",
                        "Fornecedor não encontrado com ID: " + id, 404));

        if (existing.isDeleted()) {
            throw new ApiException("SUPPLIER_DELETED", "Não é permitido alterar fornecedor deletado", 409);
        }

        if (StringUtils.hasText(req.getName())) {
            existing.setName(req.getName().trim());
        }

        if (req.getContactPerson() != null) {
            existing.setContactPerson(StringUtils.hasText(req.getContactPerson()) ? req.getContactPerson().trim() : null);
        }

        if (req.getEmail() != null) {
            existing.setEmail(StringUtils.hasText(req.getEmail()) ? req.getEmail().trim() : null);
        }

        if (req.getPhone() != null) {
            existing.setPhone(StringUtils.hasText(req.getPhone()) ? req.getPhone().trim() : null);
        }

        if (req.getAddress() != null) {
            existing.setAddress(StringUtils.hasText(req.getAddress()) ? req.getAddress().trim() : null);
        }

        if (req.getDocument() != null) {
            String newDoc = req.getDocument();
            if (StringUtils.hasText(newDoc)) {
                newDoc = newDoc.trim();

                Optional<Supplier> other = tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(newDoc);
                if (other.isPresent() && !other.get().getId().equals(id)) {
                    throw new ApiException("SUPPLIER_DOCUMENT_ALREADY_EXISTS",
                            "Já existe fornecedor com document: " + newDoc, 409);
                }

                existing.setDocument(newDoc);
            } else {
                existing.setDocument(null);
            }
        }

        if (req.getDocumentType() != null) {
            existing.setDocumentType(StringUtils.hasText(req.getDocumentType()) ? req.getDocumentType().trim() : null);
        }

        if (req.getWebsite() != null) {
            existing.setWebsite(StringUtils.hasText(req.getWebsite()) ? req.getWebsite().trim() : null);
        }

        if (req.getPaymentTerms() != null) {
            existing.setPaymentTerms(StringUtils.hasText(req.getPaymentTerms()) ? req.getPaymentTerms().trim() : null);
        }

        if (req.getLeadTimeDays() != null) {
            if (req.getLeadTimeDays() < 0) {
                throw new ApiException("INVALID_LEAD_TIME", "leadTimeDays não pode ser negativo", 400);
            }
            existing.setLeadTimeDays(req.getLeadTimeDays());
        }

        if (req.getRating() != null) {
            validateRating(req.getRating());
            existing.setRating(req.getRating());
        }

        if (req.getNotes() != null) {
            existing.setNotes(StringUtils.hasText(req.getNotes()) ? req.getNotes().trim() : null);
        }

        return tenantSupplierRepository.save(existing);
    }

    @TenantTx
    public Supplier toggleActive(UUID id) {
        Supplier supplier = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND",
                        "Fornecedor não encontrado com ID: " + id, 404));

        if (supplier.isDeleted()) {
            throw new ApiException("SUPPLIER_DELETED", "Não é permitido alterar fornecedor deletado", 409);
        }

        supplier.setActive(!supplier.isActive());
        return tenantSupplierRepository.save(supplier);
    }

    @TenantTx
    public void softDelete(UUID id) {
        Supplier supplier = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND",
                        "Fornecedor não encontrado com ID: " + id, 404));

        supplier.softDelete(appClock.instant());
        tenantSupplierRepository.save(supplier);
    }

    @TenantTx
    public Supplier restore(UUID id) {
        Supplier supplier = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException("SUPPLIER_NOT_FOUND",
                        "Fornecedor não encontrado com ID: " + id, 404));

        supplier.restore();
        return tenantSupplierRepository.save(supplier);
    }

    // =========================================================
    // Validation
    // =========================================================

    private void validateForCreate(Supplier supplier) {
        if (supplier == null) throw new ApiException("SUPPLIER_REQUIRED", "Fornecedor é obrigatório", 400);

        if (!StringUtils.hasText(supplier.getName())) {
            throw new ApiException("SUPPLIER_NAME_REQUIRED", "name é obrigatório", 400);
        }

        supplier.setName(supplier.getName().trim());

        if (supplier.getEmail() != null) {
            supplier.setEmail(StringUtils.hasText(supplier.getEmail()) ? supplier.getEmail().trim() : null);
        }
        if (supplier.getContactPerson() != null) {
            supplier.setContactPerson(StringUtils.hasText(supplier.getContactPerson()) ? supplier.getContactPerson().trim() : null);
        }
        if (supplier.getPhone() != null) {
            supplier.setPhone(StringUtils.hasText(supplier.getPhone()) ? supplier.getPhone().trim() : null);
        }
        if (supplier.getAddress() != null) {
            supplier.setAddress(StringUtils.hasText(supplier.getAddress()) ? supplier.getAddress().trim() : null);
        }
        if (supplier.getDocumentType() != null) {
            supplier.setDocumentType(StringUtils.hasText(supplier.getDocumentType()) ? supplier.getDocumentType().trim() : null);
        }
        if (supplier.getWebsite() != null) {
            supplier.setWebsite(StringUtils.hasText(supplier.getWebsite()) ? supplier.getWebsite().trim() : null);
        }
        if (supplier.getPaymentTerms() != null) {
            supplier.setPaymentTerms(StringUtils.hasText(supplier.getPaymentTerms()) ? supplier.getPaymentTerms().trim() : null);
        }
        if (supplier.getNotes() != null) {
            supplier.setNotes(StringUtils.hasText(supplier.getNotes()) ? supplier.getNotes().trim() : null);
        }

        if (supplier.getLeadTimeDays() != null && supplier.getLeadTimeDays() < 0) {
            throw new ApiException("INVALID_LEAD_TIME", "leadTimeDays não pode ser negativo", 400);
        }

        if (supplier.getRating() != null) {
            validateRating(supplier.getRating());
        }
    }

    private void validateRating(BigDecimal rating) {
        if (rating.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException("INVALID_RATING", "rating não pode ser negativo", 400);
        }
        if (rating.compareTo(new BigDecimal("9.99")) > 0) {
            throw new ApiException("INVALID_RATING", "rating máximo é 9.99", 400);
        }
    }

    @TenantReadOnlyTx
    public List<Supplier> findAnyByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException("SUPPLIER_EMAIL_REQUIRED", "email é obrigatório", 400);
        }
        return tenantSupplierRepository.findAnyByEmail(email);
    }
}
