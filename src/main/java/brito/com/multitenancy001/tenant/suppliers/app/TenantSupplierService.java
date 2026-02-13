package brito.com.multitenancy001.tenant.suppliers.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.suppliers.domain.Supplier;
import brito.com.multitenancy001.tenant.suppliers.persistence.TenantSupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório");

        Supplier s = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND));

        if (s.isDeleted()) {
            throw new ApiException(ApiErrorCode.SUPPLIER_DELETED, "Fornecedor deletado não pode ser consultado");
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
            throw new ApiException(ApiErrorCode.SUPPLIER_DOCUMENT_REQUIRED);
        }

        String doc = document.trim();

        return tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(doc)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND,
                        "Fornecedor não encontrado com document: " + doc));
    }

    @TenantReadOnlyTx
    public List<Supplier> searchByName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_NAME_REQUIRED);
        }
        return tenantSupplierRepository.findNotDeletedByNameContainingIgnoreCase(name.trim());
    }

    @TenantReadOnlyTx
    public List<Supplier> findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_EMAIL_REQUIRED, "email é obrigatório");
        }
        return tenantSupplierRepository.findNotDeletedByEmail(email.trim());
    }

    @TenantReadOnlyTx
    public List<Supplier> findAnyByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_EMAIL_REQUIRED, "email é obrigatório");
        }
        return tenantSupplierRepository.findAnyByEmail(email.trim());
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
                throw new ApiException(ApiErrorCode.SUPPLIER_DOCUMENT_ALREADY_EXISTS,
                        "Já existe fornecedor com document: " + doc);
            }
            supplier.setDocument(doc);
        } else {
            supplier.setDocument(null);
        }

        supplier.setDeleted(false);
        supplier.setActive(true);

        // se seu domínio tiver auditInfo, aqui é o lugar; mantive neutro pois não vi o modelo completo agora
        return tenantSupplierRepository.save(supplier);
    }

    @TenantTx
    public Supplier update(UUID id, Supplier req) {
        if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório");
        if (req == null) throw new ApiException(ApiErrorCode.SUPPLIER_REQUIRED);

        Supplier existing = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado com ID: " + id));

        if (existing.isDeleted()) {
            throw new ApiException(ApiErrorCode.SUPPLIER_DELETED);
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

        // document: se permitir update, faça com validação de unicidade
        if (req.getDocument() != null) {
            String newDoc = StringUtils.hasText(req.getDocument()) ? req.getDocument().trim() : null;
            if (newDoc != null) {
                tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(newDoc)
                        .ifPresent(other -> {
                            if (!other.getId().equals(id)) {
                                throw new ApiException(ApiErrorCode.SUPPLIER_DOCUMENT_ALREADY_EXISTS,
                                        "Já existe fornecedor com document: " + newDoc);
                            }
                        });
            }
            existing.setDocument(newDoc);
        }

        return tenantSupplierRepository.save(existing);
    }

    @TenantTx
    public Supplier toggleActive(UUID id) {
        if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório");

        Supplier s = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND));

        if (s.isDeleted()) {
            throw new ApiException(ApiErrorCode.SUPPLIER_DELETED, "Não é permitido alterar fornecedor deletado");
        }

        s.setActive(!s.isActive());
        return tenantSupplierRepository.save(s);
    }

    @TenantTx
    public void softDelete(UUID id) {
        if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório");

        Supplier s = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND));

        s.softDelete(); // assumindo que existe no domínio
        tenantSupplierRepository.save(s);
    }

    @TenantTx
    public Supplier restore(UUID id) {
        if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório");

        Supplier s = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado: " + id));

        s.restore(); // assumindo que existe no domínio
        return tenantSupplierRepository.save(s);
    }

    // =========================================================
    // Validations
    // =========================================================

    private void validateForCreate(Supplier supplier) {
        if (supplier == null) throw new ApiException(ApiErrorCode.SUPPLIER_REQUIRED);

        if (!StringUtils.hasText(supplier.getName())) {
            throw new ApiException(ApiErrorCode.SUPPLIER_NAME_REQUIRED, "name é obrigatório");
        }

        supplier.setName(supplier.getName().trim());
    }
}
