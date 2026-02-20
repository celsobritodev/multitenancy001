package brito.com.multitenancy001.tenant.suppliers.app.command;

import java.math.BigDecimal;

/**
 * Command de criação de Supplier.
 *
 * Nota:
 * - Commands pertencem à camada Application.
 * - Validações de regra (unicidade, bloqueio por deleted, etc.) ficam no Service.
 */
public record CreateSupplierCommand(
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