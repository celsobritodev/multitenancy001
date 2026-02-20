package brito.com.multitenancy001.tenant.suppliers.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response HTTP de Supplier.
 *
 * Nota:
 * - Expõe "deleted" para suportar endpoints/admin/consultas sem precisar trocar DTO depois.
 */
public record SupplierResponse(
        UUID id,
        String name,
        String contactPerson,
        String email,
        String phone,
        String address,
        String document,
        String documentType,
        String website,
        String paymentTerms,
        Integer leadTimeDays,
        BigDecimal rating,
        boolean active,
        boolean deleted,
        String notes
) {}