package brito.com.multitenancy001.tenant.suppliers.app.command;

import java.math.BigDecimal;

/**
 * Command de atualização de Supplier.
 *
 * Semântica:
 * - Campos null significam "não alterar" (compatível com o comportamento atual do endpoint).
 */
public record UpdateSupplierCommand(
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
        String notes
) {}