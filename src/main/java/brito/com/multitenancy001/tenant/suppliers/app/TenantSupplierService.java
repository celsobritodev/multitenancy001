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
 * Papel:
 * - Orquestrar casos de uso (commands)
 * - Aplicar regras de negócio (unicidade, bloqueios por deleted, etc.)
 * - Delegar regras intrínsecas para o Domain quando aplicável (softDelete/restore)
 *
 * Regras:
 * - Controller chama Service usando Command (não Entity, não DTO)
 * - Repository é usado apenas aqui (não no Controller)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSupplierService {

    private final TenantSupplierRepository tenantSupplierRepository;
    

    // =========================================================
    // READ (por padrão: NÃO retorna deletados)
    // =========================================================

    @TenantReadOnlyTx
    public Supplier findById(UUID id) {
        // Comentário do método: busca por id sem retornar deletados.
        if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório", 400);

        Supplier s = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado com ID: " + id, 404));

        if (s.isDeleted()) throw new ApiException(ApiErrorCode.SUPPLIER_DELETED, "Fornecedor removido", 404);
        return s;
    }

    @TenantReadOnlyTx
    public List<Supplier> findAll() {
        // Comentário do método: lista não-deletados.
        return tenantSupplierRepository.findAllNotDeleted();
    }

    @TenantReadOnlyTx
    public List<Supplier> findActive() {
        // Comentário do método: lista ativos e não-deletados.
        return tenantSupplierRepository.findAllActiveNotDeleted();
    }

    @TenantReadOnlyTx
    public Supplier findByDocument(String document) {
        // Comentário do método: busca por document (não-deletado).
        if (!StringUtils.hasText(document)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_DOCUMENT_REQUIRED, "document é obrigatório", 400);
        }
        return tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(document.trim())
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado", 404));
    }

    @TenantReadOnlyTx
    public List<Supplier> searchByName(String name) {
        // Comentário do método: busca por nome (não-deletados).
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_NAME_REQUIRED, "name é obrigatório", 400);
        }
        return tenantSupplierRepository.searchNotDeletedByName(name.trim());
    }

    @TenantReadOnlyTx
    public List<Supplier> findByEmail(String email) {
        // Comentário do método: busca por email (não-deletados).
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_EMAIL_REQUIRED, "email é obrigatório", 400);
        }
        return tenantSupplierRepository.findNotDeletedByEmail(email.trim());
    }

    // =========================================================
    // WRITE (Command-based)
    // =========================================================

    @TenantTx
    public Supplier create(CreateSupplierCommand cmd) {
        // Comentário do método: cria Supplier via Command + valida regras (unicidade, normalização).
        Supplier supplier = mapToNewEntity(cmd);

        validateForCreate(supplier);

        if (StringUtils.hasText(supplier.getDocument())) {
            String doc = supplier.getDocument().trim();
            Optional<Supplier> existing = tenantSupplierRepository.findNotDeletedByDocumentIgnoreCase(doc);
            if (existing.isPresent()) {
                throw new ApiException(
                        ApiErrorCode.SUPPLIER_DOCUMENT_ALREADY_EXISTS,
                        "Já existe fornecedor com document: " + doc,
                        409
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
        // Comentário do método: atualiza Supplier via Command (campos null não alteram).
        if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório", 400);
        if (cmd == null) throw new ApiException(ApiErrorCode.SUPPLIER_REQUIRED, "payload é obrigatório", 400);

        Supplier existing = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado com ID: " + id, 404));

        if (existing.isDeleted()) {
            throw new ApiException(ApiErrorCode.SUPPLIER_DELETED, "Não é permitido alterar fornecedor deletado", 409);
        }

        if (cmd.name() != null) {
            if (!StringUtils.hasText(cmd.name())) {
                throw new ApiException(ApiErrorCode.SUPPLIER_NAME_REQUIRED, "name é obrigatório", 400);
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
                            "Já existe fornecedor com document: " + newDoc,
                            409
                    );
                }

                existing.setDocument(newDoc);
            } else {
                existing.setDocument(null);
            }
        }

        if (cmd.documentType() != null) {
            existing.setDocumentType(StringUtils.hasText(cmd.documentType()) ? cmd.documentType().trim() : null);
        }

        if (cmd.website() != null) {
            existing.setWebsite(StringUtils.hasText(cmd.website()) ? cmd.website().trim() : null);
        }

        if (cmd.paymentTerms() != null) {
            existing.setPaymentTerms(StringUtils.hasText(cmd.paymentTerms()) ? cmd.paymentTerms().trim() : null);
        }

        if (cmd.leadTimeDays() != null) {
            if (cmd.leadTimeDays() < 0) {
                throw new ApiException(ApiErrorCode.INVALID_LEAD_TIME, "leadTimeDays não pode ser negativo", 400);
            }
            existing.setLeadTimeDays(cmd.leadTimeDays());
        }

        if (cmd.rating() != null) {
            validateRating(cmd.rating());
            existing.setRating(cmd.rating());
        }

        if (cmd.notes() != null) {
            existing.setNotes(StringUtils.hasText(cmd.notes()) ? cmd.notes().trim() : null);
        }

        return tenantSupplierRepository.save(existing);
    }

    @TenantTx
    public Supplier toggleActive(UUID id) {
        // Comentário do método: alterna active; bloqueia se deletado.
        if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório", 400);

        Supplier s = tenantSupplierRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado com ID: " + id, 404));

        if (s.isDeleted()) throw new ApiException(ApiErrorCode.SUPPLIER_DELETED, "Fornecedor removido", 409);

        s.setActive(!s.isActive());
        return tenantSupplierRepository.save(s);
    }

  @TenantTx
public void softDelete(UUID id) {
    // Comentário do método: soft-delete idempotente (204 sempre que existir, mesmo se já deletado).
    if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório", 400);

    Supplier s = tenantSupplierRepository.findById(id)
            .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado com ID: " + id, 404));

    // Idempotência: se já está deletado, não faz nada e mantém o contrato (controller retorna 204).
    if (s.isDeleted()) {
        return;
    }

    s.softDelete();
    tenantSupplierRepository.save(s);
}

@TenantTx
public Supplier restore(UUID id) {
    // Comentário do método: restore idempotente (200 com body, mesmo se já estiver restaurado).
    if (id == null) throw new ApiException(ApiErrorCode.SUPPLIER_ID_REQUIRED, "id é obrigatório", 400);

    Supplier s = tenantSupplierRepository.findById(id)
            .orElseThrow(() -> new ApiException(ApiErrorCode.SUPPLIER_NOT_FOUND, "Fornecedor não encontrado com ID: " + id, 404));

    // Idempotência: se não está deletado, retorna como está (200).
    if (!s.isDeleted()) {
        return s;
    }

    s.restore();
    return tenantSupplierRepository.save(s);
}

    @TenantReadOnlyTx
    public List<Supplier> findAnyByEmail(String email) {
        // Comentário do método: consulta "any" (inclui deletados) para diagnósticos/admin.
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.SUPPLIER_EMAIL_REQUIRED, "email é obrigatório", 400);
        }
        return tenantSupplierRepository.findAnyByEmail(email.trim());
    }

    // =========================================================
    // Helpers
    // =========================================================

    private Supplier mapToNewEntity(CreateSupplierCommand cmd) {
        // Comentário do método: cria entity "nova" a partir do command (sem persistência).
        if (cmd == null) throw new ApiException(ApiErrorCode.SUPPLIER_REQUIRED, "Fornecedor é obrigatório", 400);

        return Supplier.builder()
                .name(cmd.name())
                .contactPerson(cmd.contactPerson())
                .email(cmd.email())
                .phone(cmd.phone())
                .address(cmd.address())
                .document(cmd.document())
                .documentType(cmd.documentType())
                .website(cmd.website())
                .paymentTerms(cmd.paymentTerms())
                .leadTimeDays(cmd.leadTimeDays())
                .rating(cmd.rating())
                .notes(cmd.notes())
                .active(true)
                .deleted(false)
                .build();
    }

    void validateForCreate(Supplier supplier) {
        // Comentário do método: valida payload de criação + normaliza campos string.
        if (supplier == null) throw new ApiException(ApiErrorCode.SUPPLIER_REQUIRED, "Fornecedor é obrigatório", 400);

        if (!StringUtils.hasText(supplier.getName())) {
            throw new ApiException(ApiErrorCode.SUPPLIER_NAME_REQUIRED, "name é obrigatório", 400);
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
            throw new ApiException(ApiErrorCode.INVALID_LEAD_TIME, "leadTimeDays não pode ser negativo", 400);
        }

        if (supplier.getRating() != null) {
            validateRating(supplier.getRating());
        }
    }

    private void validateRating(BigDecimal rating) {
        // Comentário do método: valida range permitido do rating.
        if (rating.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(ApiErrorCode.INVALID_RATING, "rating não pode ser negativo", 400);
        }
        if (rating.compareTo(new BigDecimal("9.99")) > 0) {
            throw new ApiException(ApiErrorCode.INVALID_RATING, "rating máximo é 9.99", 400);
        }
    }
}