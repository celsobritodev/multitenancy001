package brito.com.multitenancy001.dtos;

import brito.com.multitenancy001.entities.account.Account;

import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String name,
        String schemaName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime trialEndDate,
        AdminUserResponse admin
) {

    public static AccountResponse fromEntity(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getSchemaName(),
                account.getStatus().name(),
                account.getCreatedAt(),
                account.getTrialEndDate(),
                null // admin só vem quando cria conta
        );
    }
}
