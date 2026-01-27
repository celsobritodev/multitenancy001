package brito.com.multitenancy001.shared.contracts;

public record UserSummaryData(
        Long id,
        Long accountId,
        String name,

        String email,
        String role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean deleted
) {}
